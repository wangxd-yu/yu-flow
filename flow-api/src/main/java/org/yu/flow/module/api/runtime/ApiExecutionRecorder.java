package org.yu.flow.module.api.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.dto.R;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.engine.log.LogMode;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.TracePersistUtil;
import org.yu.flow.engine.model.step.ResponseResult;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.log.execution.domain.FlowExecutionLogDO;
import org.yu.flow.module.log.execution.service.FlowExecutionLogService;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.util.SecretScope;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 接口执行的日志与计量：解包流程结果、非流程类型合成单步轨迹、按日志模式落库、按值脱敏敏感环境变量、记录调用计量。
 */
@Component
public class ApiExecutionRecorder {

    /** 截断后写入 TEXT 列，避免超长异常信息撑爆字段 */
    private static final int MAX_ERROR_MSG_LENGTH = 2000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @FunctionalInterface
    public interface Execution {
        Object run(RuntimeLogContext logContext) throws Exception;
    }

    private final FlowExecutionLogService flowExecutionLogService;
    private final AssetMetricsRecorder assetMetricsRecorder;
    private final YuFlowProperties yuFlowProperties;
    private final YuFlowRuntimeSettings yuFlowRuntimeSettings;

    public ApiExecutionRecorder(FlowExecutionLogService flowExecutionLogService,
                                AssetMetricsRecorder assetMetricsRecorder,
                                YuFlowProperties yuFlowProperties,
                                YuFlowRuntimeSettings yuFlowRuntimeSettings) {
        this.flowExecutionLogService = flowExecutionLogService;
        this.assetMetricsRecorder = assetMetricsRecorder;
        this.yuFlowProperties = yuFlowProperties;
        this.yuFlowRuntimeSettings = yuFlowRuntimeSettings;
    }

    /**
     * 执行一次接口调用并记录。
     *
     * @param requestParams 记入日志的请求参数，为 null 时不记
     * @return 解包后的业务结果；流程执行失败时抛出异常，失败的 {@link ExecutionResult} 转成 {@link R#fail}
     */
    public Object record(FlowApiDO flowApiDO, Object requestParams, Execution execution) throws Exception {
        try (SecretScope secrets = SecretScope.open()) {
            return recordInScope(flowApiDO, requestParams, execution, secrets);
        }
    }

    private Object recordInScope(FlowApiDO flowApiDO, Object requestParams, Execution execution,
                                 SecretScope secrets) throws Exception {
        long start = System.currentTimeMillis();
        String resolvedMode = resolveLogMode(flowApiDO);
        FlowExecutionLogDO logDO = baseLog(flowApiDO);
        RuntimeLogContext logContext = new RuntimeLogContext(resolvedMode);
        MetricsOutcome metricsOutcome = MetricsOutcome.SUCCESS;
        try {
            if (requestParams != null) {
                logDO.setRequestParams(OBJECT_MAPPER.writeValueAsString(requestParams));
            }
        } catch (Exception e) {
            logDO.setRequestParams("JSON parse error");
        }
        try {
            Object result = execution.run(logContext);
            logDO.setStatus("SUCCESS");
            Object resolved = resolveResult(result, logDO, flowApiDO, logContext);
            if (resolved instanceof R<?> r && Boolean.FALSE.equals(r.getOk())) {
                metricsOutcome = MetricsOutcome.FAIL;
                if ("SUCCESS".equals(logDO.getStatus())) {
                    logDO.setStatus("ERROR");
                    setErrorMsg(logDO, r.getMsg());
                }
            }
            return resolved;
        } catch (Exception e) {
            metricsOutcome = MetricsOutcome.FAIL;
            if (!"SUCCESS".equals(logDO.getStatus())) {
                logDO.setStatus("ERROR");
                setErrorMsg(logDO, e.getMessage());
                if (!"FLOW".equals(flowApiDO.getServiceType())) {
                    setSyntheticErrorTrace(logDO, flowApiDO, e, logContext);
                }
            }
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (flowApiDO != null && flowApiDO.getId() != null) {
                assetMetricsRecorder.record(MetricsAssetType.API, flowApiDO.getId(), metricsOutcome, cost);
            }
            boolean isSuccess = (metricsOutcome == MetricsOutcome.SUCCESS) && "SUCCESS".equals(logDO.getStatus());
            if (LogMode.shouldRecord(resolvedMode, isSuccess)) {
                if (!LogMode.shouldRecordTrace(resolvedMode, isSuccess)) {
                    logDO.setTraceData(null);
                }
                logDO.setCostTimeMs(cost);
                maskSecrets(logDO, secrets);
                flowExecutionLogService.saveLogAsync(logDO);
            }
        }
    }

    private String resolveLogMode(FlowApiDO flowApiDO) {
        String rawMode = flowApiDO != null ? flowApiDO.getLogMode() : null;
        String globalDefault = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.getEngineDefaultLogMode()
                : (yuFlowProperties != null && yuFlowProperties.getEngine() != null
                        ? yuFlowProperties.getEngine().getDefaultLogMode() : null);
        return LogMode.resolve(rawMode, globalDefault);
    }

    private static FlowExecutionLogDO baseLog(FlowApiDO flowApiDO) {
        FlowExecutionLogDO logDO = new FlowExecutionLogDO();
        logDO.setApiId(flowApiDO.getId());
        logDO.setApiName(flowApiDO.getName());
        logDO.setUrl(flowApiDO.getUrl());
        logDO.setServiceType(flowApiDO.getServiceType());
        logDO.setMethod(flowApiDO.getMethod());
        return logDO;
    }

    /** 落库前按值脱敏整条调用链用到的敏感环境变量（请求、响应、错误信息、轨迹） */
    private static void maskSecrets(FlowExecutionLogDO logDO, SecretScope secrets) {
        if (secrets.values().isEmpty()) {
            return;
        }
        logDO.setRequestParams(secrets.mask(logDO.getRequestParams()));
        logDO.setResponseBody(secrets.mask(logDO.getResponseBody()));
        logDO.setErrorMsg(secrets.mask(logDO.getErrorMsg()));
        logDO.setTraceData(secrets.mask(logDO.getTraceData()));
    }

    private static void setErrorMsg(FlowExecutionLogDO logDO, String msg) {
        if (msg == null) {
            msg = "未知错误";
        }
        logDO.setErrorMsg(msg.length() > MAX_ERROR_MSG_LENGTH ? msg.substring(0, MAX_ERROR_MSG_LENGTH) + "..." : msg);
    }

    /**
     * 统一解包执行结果并写入日志的响应体 / 轨迹：
     * <ul>
     *   <li>FlowTrace 状态为 error → 记日志后抛出 RuntimeException</li>
     *   <li>FlowTrace 输出是 ResponseResult → 非 200 或带自定义 Header 时返回原生 ResponseEntity，否则返回 body</li>
     *   <li>FlowTrace 普通输出、FLOW 的 ExecutionResult → 返回业务输出，由网关响应模板包装</li>
     *   <li>DB / JSON / STRING → 原样返回，并合成单步轨迹</li>
     * </ul>
     */
    private Object resolveResult(Object result, FlowExecutionLogDO logDO, FlowApiDO flowApiDO,
                                 RuntimeLogContext logContext) {
        boolean flow = "FLOW".equals(flowApiDO.getServiceType());
        if (result instanceof FlowTrace trace) {
            try {
                logDO.setTraceData(TracePersistUtil.serializeForPersist(
                        trace, flow ? flowApiDO.getDslContent() : null, OBJECT_MAPPER, persistOptions()));
            } catch (Exception ignored) {
            }
            if ("error".equals(trace.getStatus())) {
                String errMsg = trace.getErrorMsg() != null
                        ? trace.getErrorMsg() : "流程执行失败（引擎未返回具体错误信息）";
                logDO.setStatus("ERROR");
                setErrorMsg(logDO, errMsg);
                throw new RuntimeException(errMsg);
            }
            Object outputs = trace.getGlobalOutputs();
            if (outputs instanceof ResponseResult rr) {
                setResponseBody(logDO, rr.getBody());
                if (!bypassesResponseWrapper(rr.getStatus(), rr.getHeaders() != null && !rr.getHeaders().isEmpty())) {
                    return rr.getBody();
                }
                HttpHeaders httpHeaders = new HttpHeaders();
                if (rr.getHeaders() != null) {
                    rr.getHeaders().forEach(httpHeaders::add);
                }
                int status = Integer.parseInt(String.valueOf(rr.getStatus()));
                return new ResponseEntity<>(rr.getBody(), httpHeaders, HttpStatus.valueOf(status));
            }
            setResponseBody(logDO, outputs);
            return outputs;
        }
        if (flow && result instanceof ExecutionResult er) {
            if (!er.isSuccess()) {
                return R.fail(er.getCode(), er.getMessage());
            }
            if (er.getData() != null) {
                setResponseBody(logDO, er.getData());
            }
            return er.getData();
        }
        if (flow && result instanceof ResponseEntity<?> responseEntity) {
            if (bypassesResponseWrapper(responseEntity.getStatusCode().value(), !responseEntity.getHeaders().isEmpty())) {
                return responseEntity;
            }
            if (responseEntity.getBody() != null) {
                setResponseBody(logDO, responseEntity.getBody());
            }
            return responseEntity.getBody();
        }
        if (result != null) {
            setResponseBody(logDO, result);
        }
        if (!flow) {
            setSyntheticSuccessTrace(logDO, flowApiDO, result, logContext);
        }
        return result;
    }

    /** Response 节点默认仍走 API 响应模板；只有非 200 状态码或自定义 Header 时才按原生 HTTP 响应输出 */
    private static boolean bypassesResponseWrapper(int status, boolean hasHeaders) {
        return status != 200 || hasHeaders;
    }

    private static void setResponseBody(FlowExecutionLogDO logDO, Object body) {
        try {
            logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(body));
        } catch (Exception ignored) {
        }
    }

    private TracePersistUtil.PersistOptions persistOptions() {
        return TracePersistUtil.PersistOptions.from(yuFlowProperties != null ? yuFlowProperties.getEngine() : null);
    }

    private void setSyntheticSuccessTrace(FlowExecutionLogDO logDO, FlowApiDO flowApiDO, Object result,
                                          RuntimeLogContext logContext) {
        try {
            String serviceType = flowApiDO.getServiceType();
            Map<String, Object> inputs = requestInputs(logDO);
            if ("DB".equalsIgnoreCase(serviceType)) {
                inputs.put("actualSql", logContext.actualSqlOr(flowApiDO.getSqlContent()));
            } else if ("JSON".equalsIgnoreCase(serviceType)) {
                inputs.put("jsonContent", flowApiDO.getJsonContent());
            } else if ("STRING".equalsIgnoreCase(serviceType)) {
                inputs.put("textContent", flowApiDO.getTextContent());
            }
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", result);
            ExecutionLog stepLog = syntheticStep(serviceType, "success").setInputs(inputs).setOutputs(outputs);

            FlowTrace trace = new FlowTrace()
                    .setTraceId(UUID.randomUUID().toString())
                    .setStatus("success")
                    .setStepLogs(Collections.singletonList(stepLog))
                    .setGlobalOutputs(result);
            logDO.setTraceData(TracePersistUtil.serializeForPersist(trace, null, OBJECT_MAPPER, persistOptions()));
        } catch (Exception ignored) {
        }
    }

    private void setSyntheticErrorTrace(FlowExecutionLogDO logDO, FlowApiDO flowApiDO, Exception e,
                                        RuntimeLogContext logContext) {
        try {
            Map<String, Object> inputs = requestInputs(logDO);
            if ("DB".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("actualSql", logContext.actualSqlOr(flowApiDO.getSqlContent()));
            }
            ExecutionLog stepLog = syntheticStep(flowApiDO.getServiceType(), "error")
                    .setError(e.getMessage())
                    .setInputs(inputs);

            FlowTrace trace = new FlowTrace()
                    .setTraceId(UUID.randomUUID().toString())
                    .setStatus("error")
                    .setErrorMsg(e.getMessage())
                    .setStepLogs(Collections.singletonList(stepLog));
            logDO.setTraceData(TracePersistUtil.serializeForPersist(trace, null, OBJECT_MAPPER, persistOptions()));
        } catch (Exception ignored) {
        }
    }

    private static ExecutionLog syntheticStep(String serviceType, String status) {
        return new ExecutionLog()
                .setId("step_1")
                .setNodeId(serviceType.toLowerCase() + "_node")
                .setNodeName(serviceType + " 节点")
                .setNodeType("DB".equalsIgnoreCase(serviceType) ? "database" : serviceType.toLowerCase())
                .setStatus(status);
    }

    private static Map<String, Object> requestInputs(FlowExecutionLogDO logDO) {
        if (logDO.getRequestParams() != null) {
            try {
                return OBJECT_MAPPER.readValue(logDO.getRequestParams(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
            }
        }
        return new HashMap<>();
    }
}
