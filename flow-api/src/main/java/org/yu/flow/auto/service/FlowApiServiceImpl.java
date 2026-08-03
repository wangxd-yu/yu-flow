package org.yu.flow.auto.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.util.CamelCaseColumnMapRowMapper;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yu.flow.dto.R;
import org.yu.flow.auto.druid.DynamicSqlParser;
import org.yu.flow.auto.dto.PageBean;
import org.yu.flow.auto.dto.SqlAndParams;

import org.yu.flow.auto.util.RegularSqlParseUtil;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.service.SqlExecutorService;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.dto.FlowDbDebugRequestDTO;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.datasource.service.DynamicDataSourceService;
import org.yu.flow.module.datasource.wall.DataSourceWallGuard;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.yu.flow.module.api.service.FlowApiCrudServiceImpl;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.yu.flow.engine.log.LogMode;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.ExecutionLog;
import org.yu.flow.engine.model.TracePersistUtil;
import org.yu.flow.engine.model.step.ResponseResult;
import org.yu.flow.log.execution.domain.FlowExecutionLogDO;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.config.DemoModeGuard;
import org.yu.flow.config.SchemaValidatorService;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.log.execution.service.FlowExecutionLogService;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;

/**
 * FlowApi 执行服务实现 —— 仅负责动态 API 的运行时执行逻辑（SQL 执行、参数校验、Flow 编排引擎调用等）
 *
 * <p>所有 CRUD 管理操作已迁移至 {@link FlowApiCrudServiceImpl}。
 *
 * @author yu-flow
 * @date 2025-03-05 23:55
 */
@Slf4j
@Service
public class FlowApiServiceImpl implements FlowApiExecutionService, SqlExecutorService {

    private static final DateTimeFormatter TRACE_CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final ZoneId ZONE_SH = ZoneId.of("Asia/Shanghai");

    @Lazy
    @Resource
    private DynamicDataSourceService dynamicDataSourceService;

    @Resource
    private DataSourceWallGuard dataSourceWallGuard;

    @Resource
    private DemoModeGuard demoModeGuard;

    @Resource
    private FlowExecutionLogService flowExecutionLogService;

    @Resource
    private ContractParamTypeConverter contractParamTypeConverter;

    @Resource
    private SchemaValidatorService schemaValidatorService;

    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;

    @Resource
    private AssetMetricsRecorder assetMetricsRecorder;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ============================= 策略与调度配置 =============================

    @FunctionalInterface
    private interface ServiceTypeStrategy {
        Object execute(FlowApiDO apiDO, String content, Map<String, Object> params, Pageable pageable, HttpServletResponse response, FlowInputSupplier flowInputSupplier, RuntimeLogContext runtimeLogContext) throws Exception;
    }

    @FunctionalInterface
    private interface DbResponseStrategy {
        Object execute(FlowApiDO apiDO, SqlAndParams sqlAndParams, Pageable pageable, HttpServletResponse response) throws Exception;
    }

    private final Map<String, ServiceTypeStrategy> serviceStrategyMap = new HashMap<>();
    private final Map<String, DbResponseStrategy> dbResponseStrategyMap = new HashMap<>();

    private static class RuntimeLogContext {
        private String actualSql;
    }

    private static Object firstPresent(Map<String, Object> source, String... keys) {
        if (source == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private static Map<String, Object> toObjectMap(Object value) {
        if (!(value instanceof Map)) {
            return new HashMap<>();
        }
        Map<?, ?> source = (Map<?, ?>) value;
        Map<String, Object> result = new HashMap<>(source.size());
        source.forEach((k, v) -> {
            if (k != null) {
                result.put(String.valueOf(k), v);
            }
        });
        return result;
    }

    @jakarta.annotation.PostConstruct
    public void initStrategies() {
        serviceStrategyMap.put("FLOW", (apiDO, content, params, pageable, response, flowInputSupplier, runtimeLogContext) -> {
            FlowEngine flowEngine = new FlowEngine();
            Map<String, Object> allInputs = flowInputSupplier.get();
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("headers", toObjectMap(firstPresent(allInputs, "headers")));
            // query + path 一并进入 request.params，便于被调流程 $.request.params.xxx 读取
            Map<String, Object> mergedParams = new HashMap<>(
                    toObjectMap(firstPresent(allInputs, "queryParams", "params", "@QP")));
            mergedParams.putAll(toObjectMap(firstPresent(allInputs, "pathParams", "@PP")));
            requestMap.put("params", mergedParams);
            requestMap.put("body", toObjectMap(firstPresent(allInputs, "bodyParams", "body", "@BP")));
            // 编排入参：供 DB/表达式风格读取；Request 节点仍以 params/body 为准
            if (allInputs.get("@FP") instanceof Map<?, ?> fp && !fp.isEmpty()) {
                requestMap.put("fp", toObjectMap(fp));
            }

            Map<String, Object> flowArgs = new HashMap<>();
            flowArgs.put("request", requestMap);
            flowArgs.put("pageable", allInputs.get("pageable") != null
                    ? allInputs.get("pageable") : pageable);

            boolean traceEnabled = "ALL".equals(resolveLogMode(apiDO));
            return flowEngine.execute(content, flowArgs, traceEnabled, "API",
                    apiDO.getId(), apiDO.getName());
        });

        serviceStrategyMap.put("STRING", (apiDO, content, params, pageable, response, flowInputSupplier, runtimeLogContext) -> {
            if (JSONUtil.isTypeJSON(content)) {
                try {
                    return OBJECT_MAPPER.readValue(content, Object.class);
                } catch (Exception e) {
                    return content;
                }
            }
            return content;
        });

        serviceStrategyMap.put("JSON", (apiDO, content, params, pageable, response, flowInputSupplier, runtimeLogContext) -> {
            try {
                return OBJECT_MAPPER.readValue(content, Object.class);
            } catch (Exception e) {
                throw new RuntimeException("JSON parsing error", e);
            }
        });

        serviceStrategyMap.put("DB", (apiDO, content, params, pageable, response, flowInputSupplier, runtimeLogContext) -> {
            SqlAndParams sqlAndParams = DynamicSqlParser.parseDynamicSqlToPrepared(content, params);
            if (runtimeLogContext != null) {
                runtimeLogContext.actualSql = buildActualSqlForLog(apiDO, sqlAndParams, pageable);
            }
            return dispatchDb(apiDO, sqlAndParams, pageable, response);
        });

        serviceStrategyMap.put("EXCEL", (apiDO, content, params, pageable, response, flowInputSupplier, runtimeLogContext) ->
            Collections.singletonMap("error", "EXCEL 导出暂未实现！")
        );

        dbResponseStrategyMap.put("PAGE", (apiDO, sqlAndParams, pageable, response) ->
            executePageQuery(apiDO.getDatasource(), sqlAndParams, pageable)
        );

        dbResponseStrategyMap.put("LIST", (apiDO, sqlAndParams, pageable, response) ->
            executeListQuery(apiDO.getDatasource(), sqlAndParams, pageable)
        );

        dbResponseStrategyMap.put("OBJECT", (apiDO, sqlAndParams, pageable, response) ->
            executeObjectQuery(apiDO.getDatasource(), sqlAndParams)
        );

        dbResponseStrategyMap.put("UPDATE", (apiDO, sqlAndParams, pageable, response) ->
            executeUpdate(apiDO.getDatasource(), sqlAndParams)
        );

        dbResponseStrategyMap.put("INSERT", (apiDO, sqlAndParams, pageable, response) ->
            executeInsert(apiDO.getDatasource(), sqlAndParams)
        );
    }

    // ============================= executeApi 入口 =============================

    @Override
    public Object executeApi(FlowApiDO flowApiDO, Map<String, String> queryParams, Map<String, Object> bodyParams,
                             Map<String, Object> mergeParamsMap, Pageable pageable, HttpServletResponse response) throws Exception {
        long start = System.currentTimeMillis();
        String resolvedMode = resolveLogMode(flowApiDO);
        FlowExecutionLogDO logDO = buildBaseLogDO(flowApiDO);
        RuntimeLogContext runtimeLogContext = new RuntimeLogContext();
        MetricsOutcome metricsOutcome = MetricsOutcome.SUCCESS;
        try {
            Map<String, Object> requestMap = new HashMap<>();
            if (queryParams != null) requestMap.put("queryParams", queryParams);
            if (bodyParams != null) requestMap.put("bodyParams", bodyParams);
            logDO.setRequestParams(OBJECT_MAPPER.writeValueAsString(requestMap));
        } catch (Exception e) {
            logDO.setRequestParams("JSON parse error");
        }
        try {
            Object result = doExecute(queryParams, bodyParams, mergeParamsMap, pageable, response, flowApiDO, runtimeLogContext);
            logDO.setStatus("SUCCESS");
            Object resolved = resolveFlowResult(result, logDO, flowApiDO, runtimeLogContext);
            if (resolved instanceof R && Boolean.FALSE.equals(((R<?>) resolved).getOk())) {
                metricsOutcome = MetricsOutcome.FAIL;
                if ("SUCCESS".equals(logDO.getStatus())) {
                    logDO.setStatus("ERROR");
                    truncateAndSetErrorMsg(logDO, ((R<?>) resolved).getMsg());
                }
            }
            return resolved;
        } catch (Exception e) {
            metricsOutcome = MetricsOutcome.FAIL;
            if (!"SUCCESS".equals(logDO.getStatus())) {
                logDO.setStatus("ERROR");
                truncateAndSetErrorMsg(logDO, e.getMessage());
                if (!"FLOW".equals(flowApiDO.getServiceType())) {
                    buildAndSetSyntheticTraceForError(logDO, flowApiDO, e, runtimeLogContext);
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
                flowExecutionLogService.saveLogAsync(logDO);
            }
        }
    }

    @Override
    public Object executeApi(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable,
                             HttpServletResponse response) throws Exception {
        long start = System.currentTimeMillis();
        String resolvedMode = resolveLogMode(flowApiDO);
        FlowExecutionLogDO logDO = buildBaseLogDO(flowApiDO);
        RuntimeLogContext runtimeLogContext = new RuntimeLogContext();
        MetricsOutcome metricsOutcome = MetricsOutcome.SUCCESS;
        try {
            if (params != null) logDO.setRequestParams(OBJECT_MAPPER.writeValueAsString(params));
        } catch (Exception e) {
            logDO.setRequestParams("JSON parse error");
        }
        try {
            Object result = doExecute(params, pageable, response, flowApiDO, runtimeLogContext);
            logDO.setStatus("SUCCESS");
            Object resolved = resolveFlowResult(result, logDO, flowApiDO, runtimeLogContext);
            if (resolved instanceof R && Boolean.FALSE.equals(((R<?>) resolved).getOk())) {
                metricsOutcome = MetricsOutcome.FAIL;
                if ("SUCCESS".equals(logDO.getStatus())) {
                    logDO.setStatus("ERROR");
                    truncateAndSetErrorMsg(logDO, ((R<?>) resolved).getMsg());
                }
            }
            return resolved;
        } catch (Exception e) {
            metricsOutcome = MetricsOutcome.FAIL;
            if (!"SUCCESS".equals(logDO.getStatus())) {
                logDO.setStatus("ERROR");
                truncateAndSetErrorMsg(logDO, e.getMessage());
                if (!"FLOW".equals(flowApiDO.getServiceType())) {
                    buildAndSetSyntheticTraceForError(logDO, flowApiDO, e, runtimeLogContext);
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

    @Override
    public FlowTrace debugRunDb(FlowDbDebugRequestDTO request) {
        long startMs = System.currentTimeMillis();
        String startTimeStr = LocalTime.now(ZONE_SH).format(TRACE_CLOCK);

        if (StrUtil.isBlank(request.getSqlContent())) {
            return buildDbDebugErrorTrace(startMs, startTimeStr, "SQL 内容不能为空", null, null);
        }
        if (StrUtil.isBlank(request.getDatasource())) {
            return buildDbDebugErrorTrace(startMs, startTimeStr, "请选择数据源", null, null);
        }
        if (StrUtil.isBlank(request.getResponseType())) {
            return buildDbDebugErrorTrace(startMs, startTimeStr, "请选择响应类型", null, null);
        }

        FlowApiDO apiDO = new FlowApiDO()
                .setId(request.getSourceRef())
                .setName(StrUtil.blankToDefault(request.getSourceName(), "DB Debug"))
                .setServiceType("DB")
                .setSqlContent(request.getSqlContent())
                .setDatasource(request.getDatasource())
                .setResponseType(request.getResponseType())
                .setPublishStatus(0)
                .setLogEnabled(false);

        Map<String, String> queryParams = request.getQueryParams() != null
                ? new LinkedHashMap<>(request.getQueryParams()) : new LinkedHashMap<>();
        Map<String, Object> bodyParams = parseDebugBody(request.getBody());
        Map<String, Object> headers = new LinkedHashMap<>();
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(headers::put);
        }

        Map<String, Object> execQuery = new LinkedHashMap<>(queryParams);
        Map<String, Object> execBody = bodyParams;
        Map<String, Object> execHeaders = headers;
        Map<String, Object> execPath = new LinkedHashMap<>();

        if (StrUtil.isNotBlank(request.getContract())) {
            String contract = request.getContract();
            try {
                execQuery = contractParamTypeConverter.convertSection(contract, "query", queryParams);
                execBody = contractParamTypeConverter.convertSection(contract, "body", bodyParams);
                execHeaders = contractParamTypeConverter.convertSection(contract, "headers", headers);
                execPath = contractParamTypeConverter.convertSection(contract, "pathParams", queryParams);
                schemaValidatorService.validateFromContract(
                        contract, execBody, execQuery, execPath, execHeaders);
            } catch (Exception e) {
                return buildDbDebugErrorTrace(startMs, startTimeStr, e.getMessage(), null, null);
            }
        }

        int page = resolveInt(request.getPage(), queryParams.get("page"), 0);
        int size = resolveInt(request.getSize(), queryParams.get("size"), 10);
        if (size <= 0) {
            size = 10;
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);

        Map<String, Object> inputParamsMap = new HashMap<>(8);
        inputParamsMap.put("@QP", execQuery);
        inputParamsMap.put("@BP", execBody);
        inputParamsMap.put("@PP", execPath);
        inputParamsMap.put("headers", execHeaders);
        inputParamsMap.put("params", execQuery);
        inputParamsMap.put("queryParams", execQuery);
        inputParamsMap.put("body", execBody);
        inputParamsMap.put("bodyParams", execBody);

        RuntimeLogContext runtimeLogContext = new RuntimeLogContext();
        boolean rollbackTransaction = request.getRollbackTransaction() == null
                || Boolean.TRUE.equals(request.getRollbackTransaction());
        try {
            Object result;
            if (rollbackTransaction) {
                result = dynamicDataSourceService.executeInTransactionThenRollback(
                        request.getDatasource(),
                        jt -> {
                            try {
                                return doExecute(inputParamsMap, pageable, null, apiDO, runtimeLogContext);
                            } catch (RuntimeException re) {
                                throw re;
                            } catch (Exception ex) {
                                throw new RuntimeException(ex.getMessage(), ex);
                            }
                        });
            } else {
                result = doExecute(inputParamsMap, pageable, null, apiDO, runtimeLogContext);
            }
            long endMs = System.currentTimeMillis();

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("queryParams", queryParams);
            inputs.put("bodyParams", bodyParams);
            inputs.put("headers", headers);
            inputs.put("datasource", apiDO.getDatasource());
            inputs.put("responseType", apiDO.getResponseType());
            inputs.put("rollbackTransaction", rollbackTransaction);
            inputs.put("actualSql", resolveActualSqlForLog(apiDO, runtimeLogContext));

            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("result", result);
            if (rollbackTransaction) {
                outputs.put("transactionNote", "已开启事务回退：本次调试中的写操作不会提交到数据库");
            }

            ExecutionLog stepLog = new ExecutionLog()
                    .setId("step_1")
                    .setNodeId("db_node")
                    .setNodeName("Database")
                    .setNodeType("database")
                    .setStatus("success")
                    .setStartTime(startTimeStr)
                    .setDuration(endMs - startMs)
                    .setInputs(inputs)
                    .setOutputs(outputs);

            return new FlowTrace()
                    .setTraceId(UUID.randomUUID().toString())
                    .setStartTime(startMs)
                    .setEndTime(endMs)
                    .setTotalDurationMs(endMs - startMs)
                    .setStatus("success")
                    .setGlobalInputs(inputs)
                    .setGlobalOutputs(result)
                    .setStepLogs(Collections.singletonList(stepLog));
        } catch (Exception e) {
            log.error("DB debug run failed", e);
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("queryParams", queryParams);
            inputs.put("bodyParams", bodyParams);
            inputs.put("actualSql", resolveActualSqlForLog(apiDO, runtimeLogContext));
            return buildDbDebugErrorTrace(startMs, startTimeStr, e.getMessage(), inputs, e);
        }
    }

    private FlowTrace buildDbDebugErrorTrace(long startMs, String startTimeStr, String errorMsg,
                                             Map<String, Object> inputs, Exception e) {
        long endMs = System.currentTimeMillis();
        String msg = StrUtil.blankToDefault(errorMsg, e != null ? e.getClass().getSimpleName() : "未知错误");
        ExecutionLog stepLog = new ExecutionLog()
                .setId("step_1")
                .setNodeId("db_node")
                .setNodeName("Database")
                .setNodeType("database")
                .setStatus("error")
                .setStartTime(startTimeStr)
                .setDuration(endMs - startMs)
                .setInputs(inputs != null ? inputs : Collections.emptyMap())
                .setError(msg);
        return new FlowTrace()
                .setTraceId(UUID.randomUUID().toString())
                .setStartTime(startMs)
                .setEndTime(endMs)
                .setTotalDurationMs(endMs - startMs)
                .setStatus("error")
                .setErrorMsg(msg)
                .setStepLogs(Collections.singletonList(stepLog));
    }

    private Map<String, Object> parseDebugBody(String body) {
        if (StrUtil.isBlank(body)) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(body, Object.class);
            if (parsed instanceof Map) {
                return toObjectMap(parsed);
            }
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("value", parsed);
            return wrap;
        } catch (Exception e) {
            Map<String, Object> wrap = new LinkedHashMap<>();
            wrap.put("raw", body);
            return wrap;
        }
    }

    private int resolveInt(Integer primary, String fromQuery, int defaultValue) {
        if (primary != null) {
            return primary;
        }
        if (StrUtil.isNotBlank(fromQuery)) {
            try {
                return Integer.parseInt(fromQuery.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    /**
     * 构建日志 DO 基础字段（共用逻辑提取）
     */
    private FlowExecutionLogDO buildBaseLogDO(FlowApiDO flowApiDO) {
        FlowExecutionLogDO logDO = new FlowExecutionLogDO();
        logDO.setApiId(flowApiDO.getId());
        logDO.setApiName(flowApiDO.getName());
        logDO.setUrl(flowApiDO.getUrl());
        logDO.setServiceType(flowApiDO.getServiceType());
        logDO.setMethod(flowApiDO.getMethod());
        return logDO;
    }

    /**
     * 截断并设置 errorMsg，防止超出 TEXT 字段长度限制（65535 字节）。
     * 保守取前 2000 字符。
     */
    private void truncateAndSetErrorMsg(
            FlowExecutionLogDO logDO, String msg) {
        if (msg == null) msg = "未知错误";
        logDO.setErrorMsg(msg.length() > 2000 ? msg.substring(0, 2000) + "..." : msg);
    }

    /**
     * 统一 FlowTrace 解包与业务响应提取（问题四：消除两个重载的代码重复；问题五：修复 null errorMsg）。
     *
     * <p>规则：
     * <ul>
     *   <li>若 result 是 FlowTrace 且状态为 error → 更新 logDO 并抛出 RuntimeException</li>
     *   <li>若 result 是 FlowTrace 且输出包含 ResponseResult → 解包为原生 ResponseEntity</li>
     *   <li>若 result 是 FlowTrace 且为普通输出 → 返回业务输出，由网关响应模板统一包装</li>
     *   <li>若 result 是 FLOW 的 ExecutionResult → 解出业务输出，由网关响应模板统一包装</li>
     *   <li>其他类型（DB / JSON / STRING）→ 直接返回，仅记录 responseBody</li>
     * </ul>
     */
    private Object resolveFlowResult(
            Object result,
            FlowExecutionLogDO logDO,
            FlowApiDO flowApiDO,
            RuntimeLogContext runtimeLogContext) throws Exception {
        if (result instanceof FlowTrace) {
            FlowTrace trace = (FlowTrace) result;
            if (logDO != null) {
                try {
                    String dsl = "FLOW".equals(flowApiDO.getServiceType())
                            ? flowApiDO.getDslContent() : null;
                    TracePersistUtil.PersistOptions opts = TracePersistUtil.PersistOptions.from(
                            yuFlowProperties != null ? yuFlowProperties.getEngine() : null);
                    logDO.setTraceData(TracePersistUtil.serializeForPersist(
                            trace, dsl, OBJECT_MAPPER, opts));
                } catch (Exception ignored) {}
            }

            if ("error".equals(trace.getStatus())) {
                // 问题五修复：errorMsg 可能为 null，改用兜底文案
                String errMsg = trace.getErrorMsg() != null
                        ? trace.getErrorMsg() : "流程执行失败（引擎未返回具体错误信息）";
                if (logDO != null) {
                    logDO.setStatus("ERROR");
                    truncateAndSetErrorMsg(logDO, errMsg);
                }
                throw new RuntimeException(errMsg);
            }

            Object outputs = trace.getGlobalOutputs();
            if (outputs instanceof ResponseResult) {
                ResponseResult rr = (ResponseResult) outputs;
                Object body = rr.getBody();
                if (logDO != null) {
                    try {
                        logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(body));
                    } catch (Exception ignored) {}
                }
                if (!shouldBypassResponseWrapper(rr.getStatus(), rr.getHeaders())) {
                    return body;
                }
                org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
                if (rr.getHeaders() != null) {
                    rr.getHeaders().forEach(httpHeaders::add);
                }
                int status = Integer.parseInt(String.valueOf(rr.getStatus()));
                return new org.springframework.http.ResponseEntity<>(
                        body, httpHeaders, org.springframework.http.HttpStatus.valueOf(status));
            } else {
                if (logDO != null) {
                    try {
                        logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(outputs));
                    } catch (Exception ignored) {}
                }
                return outputs;
            }
        } else if ("FLOW".equals(flowApiDO.getServiceType()) && result instanceof ExecutionResult) {
            ExecutionResult er = (ExecutionResult) result;
            if (!er.isSuccess()) {
                return R.fail(er.getCode(), er.getMessage());
            }
            Object outputs = er.getData();
            if (logDO != null && outputs != null) {
                try {
                    logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(outputs));
                } catch (Exception ignored) {}
            }
            return outputs;
        } else if ("FLOW".equals(flowApiDO.getServiceType()) && result instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            if (shouldBypassResponseWrapper(responseEntity.getStatusCodeValue(), responseEntity.getHeaders())) {
                return responseEntity;
            }
            Object body = responseEntity.getBody();
            if (logDO != null && body != null) {
                try {
                    logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(body));
                } catch (Exception ignored) {}
            }
            return body;
        } else {
            // 非 Flow 类型的返回值处理与合成快照构建
            if (logDO != null && result != null) {
                try {
                    logDO.setResponseBody(OBJECT_MAPPER.writeValueAsString(result));
                } catch (Exception ignored) {}
            }
            if (logDO != null && !"FLOW".equals(flowApiDO.getServiceType())) {
                buildAndSetSyntheticTraceForSuccess(logDO, flowApiDO, result, runtimeLogContext);
            }
        }
        return result;
    }

    /**
     * Response 节点默认仍走 API 响应模板；只有显式使用自定义 Header 或非 200 状态码时才按原生 HTTP 响应输出。
     */
    private boolean shouldBypassResponseWrapper(int status, Map<String, String> headers) {
        return status != 200 || (headers != null && !headers.isEmpty());
    }

    private boolean shouldBypassResponseWrapper(int status, HttpHeaders headers) {
        return status != 200 || (headers != null && !headers.isEmpty());
    }

    private void buildAndSetSyntheticTraceForSuccess(
            FlowExecutionLogDO logDO, 
            FlowApiDO flowApiDO, 
            Object result,
            RuntimeLogContext runtimeLogContext) {
        try {
            FlowTrace trace = new FlowTrace();
            trace.setTraceId(UUID.randomUUID().toString());
            trace.setStatus("success");
            
            ExecutionLog stepLog = new ExecutionLog();
            stepLog.setId("step_1");
            stepLog.setNodeId(flowApiDO.getServiceType().toLowerCase() + "_node");
            stepLog.setNodeName(flowApiDO.getServiceType() + " 节点");
            stepLog.setNodeType("DB".equalsIgnoreCase(flowApiDO.getServiceType()) ? "database" : flowApiDO.getServiceType().toLowerCase());
            stepLog.setStatus("success");
            
            Map<String, Object> inputs = new HashMap<>();
            if (logDO.getRequestParams() != null) {
                try {
                    inputs = OBJECT_MAPPER.readValue(logDO.getRequestParams(), new TypeReference<Map<String, Object>>(){});
                } catch(Exception ignored){}
            }
            if ("DB".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("actualSql", resolveActualSqlForLog(flowApiDO, runtimeLogContext)); 
            } else if ("JSON".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("jsonContent", flowApiDO.getJsonContent());
            } else if ("STRING".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("textContent", flowApiDO.getTextContent());
            }
            stepLog.setInputs(inputs);
            
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", result);
            stepLog.setOutputs(outputs);
            
            trace.setStepLogs(Collections.singletonList(stepLog));
            trace.setGlobalOutputs(result);

            TracePersistUtil.PersistOptions opts = TracePersistUtil.PersistOptions.from(
                    yuFlowProperties != null ? yuFlowProperties.getEngine() : null);
            logDO.setTraceData(TracePersistUtil.serializeForPersist(trace, null, OBJECT_MAPPER, opts));
        } catch (Exception ignored) {}
    }

    private void buildAndSetSyntheticTraceForError(
            FlowExecutionLogDO logDO, 
            FlowApiDO flowApiDO, 
            Exception e,
            RuntimeLogContext runtimeLogContext) {
        try {
            FlowTrace trace = new FlowTrace();
            trace.setTraceId(UUID.randomUUID().toString());
            trace.setStatus("error");
            trace.setErrorMsg(e.getMessage());
            
            ExecutionLog stepLog = new ExecutionLog();
            stepLog.setId("step_1");
            stepLog.setNodeId(flowApiDO.getServiceType().toLowerCase() + "_node");
            stepLog.setNodeName(flowApiDO.getServiceType() + " 节点");
            stepLog.setNodeType("DB".equalsIgnoreCase(flowApiDO.getServiceType()) ? "database" : flowApiDO.getServiceType().toLowerCase());
            stepLog.setStatus("error");
            stepLog.setError(e.getMessage());
            
            Map<String, Object> inputs = new HashMap<>();
            if (logDO.getRequestParams() != null) {
                try {
                    inputs = OBJECT_MAPPER.readValue(logDO.getRequestParams(), new TypeReference<Map<String, Object>>(){});
                } catch(Exception ignored){}
            }
            if ("DB".equalsIgnoreCase(flowApiDO.getServiceType())) {
                inputs.put("actualSql", resolveActualSqlForLog(flowApiDO, runtimeLogContext)); 
            }
            stepLog.setInputs(inputs);
            
            trace.setStepLogs(Collections.singletonList(stepLog));
            TracePersistUtil.PersistOptions opts = TracePersistUtil.PersistOptions.from(
                    yuFlowProperties != null ? yuFlowProperties.getEngine() : null);
            logDO.setTraceData(TracePersistUtil.serializeForPersist(trace, null, OBJECT_MAPPER, opts));
        } catch (Exception ignored) {}
    }

    // ============================= 核心执行逻辑 =============================

    /**
     * 分离参数模式执行
     */
    private Object doExecute(Map<String, String> queryParams, Map<String, Object> bodyParams,
                             Map<String, Object> mergeParamsMap, Pageable pageable,
                             HttpServletResponse response, FlowApiDO flowApiDO,
                             RuntimeLogContext runtimeLogContext) throws Exception {
        // 参数类型转换 + JSON Schema 校验：已发布时读快照契约，避免草稿污染线上
        String runtimeContract = resolveContract(flowApiDO);
        Map<String, Object> typedQueryParams =
                contractParamTypeConverter.convertSection(runtimeContract, "query", queryParams);
        Map<String, Object> typedBodyParams =
                contractParamTypeConverter.convertSection(runtimeContract, "body", bodyParams);
        Map<String, Object> typedPathParams = contractParamTypeConverter.convertSection(
                runtimeContract, "pathParams",
                toObjectMap(firstPresent(mergeParamsMap, "pathParams", "@PP")));
        Map<String, Object> typedHeaders = contractParamTypeConverter.convertSection(
                runtimeContract, "headers",
                toObjectMap(firstPresent(mergeParamsMap, "headers")));

        if (StrUtil.isNotBlank(runtimeContract)) {
            schemaValidatorService.validateFromContract(
                    runtimeContract, typedBodyParams, typedQueryParams, typedPathParams, typedHeaders);
        }

        Map<String, Object> typedMergeParams =
                mergeParamsMap == null ? new HashMap<>() : new HashMap<>(mergeParamsMap);
        typedQueryParams.forEach((key, value) -> {
            typedMergeParams.put("query." + key, value);
            typedMergeParams.putIfAbsent(key, value);
        });
        typedBodyParams.forEach((key, value) -> {
            typedMergeParams.put("body." + key, value);
            typedMergeParams.put(key, value);
        });
        typedMergeParams.put("@PP", typedPathParams);
        typedMergeParams.put("pathParams", typedPathParams);
        typedMergeParams.put("headers", typedHeaders);

        // 根据请求类型分发
        return dispatch(flowApiDO, typedMergeParams, pageable, response, () -> {
            Map<String, Object> inputsMap = new HashMap<>();
            inputsMap.put("queryParams", typedQueryParams);
            inputsMap.put("bodyParams", typedBodyParams);
            inputsMap.put("mergeParams", typedMergeParams);
            inputsMap.put("pageable", pageable);
            return inputsMap;
        }, runtimeLogContext);
    }

    /**
     * 合并参数模式执行
     */
    private Object doExecute(Map<String, Object> params, Pageable pageable,
                             HttpServletResponse response, FlowApiDO flowApiDO,
                             RuntimeLogContext runtimeLogContext) throws Exception {
        Map<String, Object> typedParams = convertContextParams(resolveContract(flowApiDO), params);
        return dispatch(flowApiDO, typedParams, pageable, response, () -> {
            Map<String, Object> inputsMapObj = new HashMap<>();
            inputsMapObj.put("pageable", pageable);
            inputsMapObj.putAll(typedParams);
            return inputsMapObj;
        }, runtimeLogContext);
    }

    private Map<String, Object> convertContextParams(String contract, Map<String, Object> params) {
        Map<String, Object> result = params == null ? new HashMap<>() : new HashMap<>(params);
        Object querySource = firstPresent(result, "params", "queryParams", "@QP");
        Object bodySource = firstPresent(result, "body", "bodyParams", "@BP");

        Map<String, Object> query = contractParamTypeConverter.convertSection(
                contract, "query", querySource == null ? result : toObjectMap(querySource));
        Map<String, Object> body = contractParamTypeConverter.convertSection(
                contract, "body", bodySource == null ? Collections.emptyMap() : toObjectMap(bodySource));
        Map<String, Object> headers = contractParamTypeConverter.convertSection(
                contract, "headers", toObjectMap(firstPresent(result, "headers")));
        Map<String, Object> pathParams = contractParamTypeConverter.convertSection(
                contract, "pathParams", toObjectMap(firstPresent(result, "pathParams", "@PP")));

        result.put("@QP", query);
        result.put("@BP", body);
        result.put("@PP", pathParams);
        result.put("params", query);
        result.put("queryParams", query);
        result.put("body", body);
        result.put("bodyParams", body);
        result.put("headers", headers);
        result.putAll(query);
        result.putAll(body);
        return result;
    }

    /**
     * 统一分发器：根据 serviceType 路由到不同的执行策略
     */
    @FunctionalInterface
    private interface FlowInputSupplier {
        Map<String, Object> get();
    }

    private Object dispatch(FlowApiDO flowApiDO, Map<String, Object> params, Pageable pageable,
                            HttpServletResponse response, FlowInputSupplier flowInputSupplier,
                            RuntimeLogContext runtimeLogContext) throws Exception {
        // 根据 serviceType 精准读取对应的隔离字段
        String content = resolveContent(flowApiDO);

        ServiceTypeStrategy strategy = serviceStrategyMap.get(flowApiDO.getServiceType());
        if (strategy == null) {
            throw new UnsupportedOperationException("接口配置错误，未知的 serviceType: " + flowApiDO.getServiceType());
        }
        return strategy.execute(flowApiDO, content, params, pageable, response, flowInputSupplier, runtimeLogContext);
    }

    private String resolveActualSqlForLog(FlowApiDO flowApiDO, RuntimeLogContext runtimeLogContext) {
        if (runtimeLogContext != null && StrUtil.isNotBlank(runtimeLogContext.actualSql)) {
            return runtimeLogContext.actualSql;
        }
        return flowApiDO.getSqlContent();
    }

    private String buildActualSqlForLog(FlowApiDO flowApiDO, SqlAndParams sqlAndParams, Pageable pageable) {
        String responseType = flowApiDO.getResponseType();
        if ("PAGE".equalsIgnoreCase(responseType) && pageable != null) {
            String countSql = buildCountSql(sqlAndParams.getSql());
            String pageSql = buildPageSql(sqlAndParams.getSql(), pageable);
            return "COUNT SQL:\n" + inlinePreparedSql(countSql, sqlAndParams.getParams())
                    + "\n\nPAGE SQL:\n" + inlinePreparedSql(pageSql, sqlAndParams.getParams());
        }

        String sql = sqlAndParams.getSql();
        if ("LIST".equalsIgnoreCase(responseType) && pageable != null && pageable.getSort().isSorted()) {
            sql = RegularSqlParseUtil.removeOrderByClause(sql);
            sql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
        }
        return inlinePreparedSql(sql, sqlAndParams.getParams());
    }

    private String buildCountSql(String originalSql) {
        String countSql;
        if (originalSql.toUpperCase().contains("WITH")) {
            String withClause = RegularSqlParseUtil.extractWithClause(originalSql);
            String mainQuery = originalSql.substring(withClause.length());
            countSql = withClause + "SELECT COUNT(*) FROM (" + mainQuery + ") AS total";
        } else {
            countSql = "SELECT COUNT(*) FROM (" + originalSql + ") AS total";
        }
        countSql = RegularSqlParseUtil.removeOrderByClause(countSql);
        return RegularSqlParseUtil.removeLimitAndOffset(countSql);
    }

    private String buildPageSql(String originalSql, Pageable pageable) {
        String pageSql = originalSql;
        if (pageable.getSort().isSorted()) {
            pageSql = RegularSqlParseUtil.removeOrderByClause(pageSql);
            pageSql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
        }
        int offset = pageable.getPageNumber() * pageable.getPageSize();
        return pageSql + String.format(" LIMIT %d OFFSET %d", pageable.getPageSize(), offset);
    }

    private String inlinePreparedSql(String preparedSql, List<Object> params) {
        if (params == null || params.isEmpty()) {
            return preparedSql;
        }
        String result = preparedSql;
        for (Object param : params) {
            result = result.replaceFirst("\\?", java.util.regex.Matcher.quoteReplacement(formatSqlValue(param)));
        }
        return result;
    }

    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "TRUE" : "FALSE";
        }
        String escaped = String.valueOf(value).replace("'", "''");
        return "'" + escaped + "'";
    }

    /**
     * 根据 serviceType 精准读取对应的隔离内容字段。
     *
     * <p>版本快照策略：已发布且存在 publishedSnapshot 时，从快照 JSON 中读取，
     * 保证草稿编辑不影响线上运行时。</p>
     *
     * @param flowApiDO API 定义实体
     * @return 当前引擎模式对应的脚本/配置内容
     */
    private String resolveContent(FlowApiDO flowApiDO) {
        // ── 快照优先策略 ──
        if (flowApiDO.getPublishStatus() != null
                && flowApiDO.getPublishStatus() == 1
                && flowApiDO.getPublishedSnapshot() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode snap = OBJECT_MAPPER.readTree(flowApiDO.getPublishedSnapshot());
                String serviceType = snap.has("serviceType") && !snap.get("serviceType").isNull()
                        ? snap.get("serviceType").asText() : flowApiDO.getServiceType();
                return getSnapshotField(snap, serviceType);
            } catch (Exception e) {
                log.warn("[FlowApiService] 解析 publishedSnapshot 失败，降级为草稿字段读取。apiId={}", flowApiDO.getId(), e);
            }
        }

        // ── 降级：直接从草稿字段读取 ──
        return resolveContentFromDraft(flowApiDO);
    }

    /** 已发布时从快照读取契约，保证运行时与内容快照一致 */
    private String resolveContract(FlowApiDO flowApiDO) {
        return PublishedApiSnapshot.resolveContract(flowApiDO);
    }

    private String resolveContentFromDraft(FlowApiDO flowApiDO) {
        String serviceType = flowApiDO.getServiceType();
        switch (serviceType) {
            case "FLOW":
                return flowApiDO.getDslContent();
            case "DB":
                return flowApiDO.getSqlContent();
            case "JSON":
                return flowApiDO.getJsonContent();
            case "STRING":
                return flowApiDO.getTextContent();
            default:
                return null;
        }
    }

    private String getSnapshotField(com.fasterxml.jackson.databind.JsonNode snap, String serviceType) {
        String fieldName;
        switch (serviceType) {
            case "FLOW":  fieldName = "dslContent"; break;
            case "DB":    fieldName = "sqlContent"; break;
            case "JSON":  fieldName = "jsonContent"; break;
            case "STRING": fieldName = "textContent"; break;
            default: return null;
        }
        com.fasterxml.jackson.databind.JsonNode node = snap.get(fieldName);
        return node != null && !node.isNull() ? node.asText() : null;
    }

    /**
     * DB 类型二级分发：根据 responseType 执行不同的数据库操作
     */
    private Object dispatchDb(FlowApiDO flowApiDO, SqlAndParams sqlAndParams, Pageable pageable,
                              HttpServletResponse response) throws Exception {
        DbResponseStrategy strategy = dbResponseStrategyMap.get(flowApiDO.getResponseType());
        if (strategy == null) {
            throw new UnsupportedOperationException("业务异常：未知的 responseType: " + flowApiDO.getResponseType());
        }
        return strategy.execute(flowApiDO, sqlAndParams, pageable, response);
    }

    // ============================= SQL 执行器（实现 SqlExecutorService） =============================

    @Override
    public Object executePageQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable) {
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        String originalSql = sqlAndParams.getSql();
        String countSql;

        if (originalSql.toUpperCase().contains("WITH")) {
            String withClause = RegularSqlParseUtil.extractWithClause(originalSql);
            String mainQuery = originalSql.substring(withClause.length());
            countSql = withClause + "SELECT COUNT(*) FROM (" + mainQuery + ") AS total";
        } else {
            countSql = "SELECT COUNT(*) FROM (" + originalSql + ") AS total";
        }

        countSql = RegularSqlParseUtil.removeOrderByClause(countSql);
        countSql = RegularSqlParseUtil.removeLimitAndOffset(countSql);

        String finalCountSql = countSql;
        Integer totalSize = dynamicDataSourceService.execute(datasource, jt -> jt.queryForObject(
                finalCountSql,
                sqlAndParams.getParams().toArray(),
                Integer.class));
        if (totalSize == null || totalSize == 0) {
            return new PageBean<>(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0L);
        }

        int totalPage = (int) Math.ceil((double) totalSize / pageable.getPageSize());
        int offset = pageable.getPageNumber() * pageable.getPageSize();

        String pageSql = sqlAndParams.getSql();
        if (pageable.getSort().isSorted()) {
            pageSql = RegularSqlParseUtil.removeOrderByClause(pageSql);
            pageSql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
        }
        // 安全性修复：强制使用 %d 整数格式化拼接，阻断任何可能的注入
        pageSql += String.format(" LIMIT %d OFFSET %d", pageable.getPageSize(), offset);

        String finalPageSql = pageSql;
        List<Map<String, Object>> content = dynamicDataSourceService.executeInTransaction(datasource, jt -> jt.query(
                finalPageSql,
                sqlAndParams.getParams().toArray(),
                new CamelCaseColumnMapRowMapper(true)));

        return new PageBean<>(content, pageable.getPageNumber(), pageable.getPageSize(), totalPage, totalSize.longValue());
    }

    @Override
    public int executeUpdate(String datasource, SqlAndParams sqlAndParams) {
        // [Demo 模式] 禁止执行 UPDATE / DELETE SQL
        demoModeGuard.checkSqlWrite("UPDATE/DELETE");
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        return dynamicDataSourceService.executeInTransaction(datasource, jt -> jt.update(
                sqlAndParams.getSql(),
                sqlAndParams.getParams().toArray()
        ));
    }

    @Override
    public int executeInsert(String datasource, SqlAndParams sqlAndParams) {
        // [Demo 模式] 禁止执行 INSERT SQL
        demoModeGuard.checkSqlWrite("INSERT");
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        return dynamicDataSourceService.executeInTransaction(datasource, jt -> jt.update(
                sqlAndParams.getSql(),
                sqlAndParams.getParams().toArray()
        ));
    }

    @Override
    public int executeBatchInsert(String datasource, String sqlTemplate,
                                  List<Map<String, Object>> rows) {
        demoModeGuard.checkSqlWrite("INSERT");
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (StrUtil.isBlank(sqlTemplate)) {
            throw new IllegalArgumentException("batch insert sqlTemplate 不能为空");
        }

        final int chunkSize = 500;
        return dynamicDataSourceService.executeInTransaction(datasource, jt -> {
            int total = 0;
            String preparedSql = null;
            List<Object[]> batchArgs = new ArrayList<>(Math.min(rows.size(), chunkSize));

            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                if (row == null) {
                    row = Collections.emptyMap();
                }
                SqlAndParams sp = DynamicSqlParser.parseDynamicSqlToPrepared(sqlTemplate, row);
                dataSourceWallGuard.assertSqlAllowed(datasource, sp.getSql());
                if (preparedSql == null) {
                    preparedSql = sp.getSql();
                } else if (!preparedSql.equals(sp.getSql())) {
                    // 参数缺失会导致动态 SQL 变形；先刷掉已攒批次再换 SQL
                    if (!batchArgs.isEmpty()) {
                        total += sumBatchUpdate(jt.batchUpdate(preparedSql, batchArgs));
                        batchArgs = new ArrayList<>(chunkSize);
                    }
                    preparedSql = sp.getSql();
                }
                batchArgs.add(sp.getParams().toArray());

                if (batchArgs.size() >= chunkSize || i == rows.size() - 1) {
                    total += sumBatchUpdate(jt.batchUpdate(preparedSql, batchArgs));
                    batchArgs = new ArrayList<>(chunkSize);
                }
            }
            return total;
        });
    }

    private static int sumBatchUpdate(int[] counts) {
        if (counts == null || counts.length == 0) {
            return 0;
        }
        int sum = 0;
        for (int c : counts) {
            // SUCCESS_NO_INFO = -2：多数驱动表示执行成功但行数未知
            sum += (c >= 0) ? c : 1;
        }
        return sum;
    }

    @Override
    public Object executeListQuery(String datasource, SqlAndParams sqlAndParams, Pageable pageable) {
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        String pageSql = sqlAndParams.getSql();
        if (pageable.getSort().isSorted()) {
            pageSql = RegularSqlParseUtil.removeOrderByClause(pageSql);
            pageSql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
        }
        String finalPageSql = pageSql;
        return dynamicDataSourceService.execute(datasource, jt -> jt.query(
                finalPageSql,
                sqlAndParams.getParams().toArray(),
                new CamelCaseColumnMapRowMapper(true)));
    }

    @Override
    public Map<String, Object> executeObjectQuery(String datasource, SqlAndParams sqlAndParams) {
        dataSourceWallGuard.assertSqlAllowed(datasource, sqlAndParams.getSql());
        List<Map<String, Object>> result = dynamicDataSourceService.execute(datasource, jt -> jt.query(
                sqlAndParams.getSql(),
                sqlAndParams.getParams().toArray(),
                new CamelCaseColumnMapRowMapper(true)));
        if (result.isEmpty()) {
            return null;
        }
        return result.get(0);
    }

    // ============================= Excel 导出（暂留） =============================

    private void exportToExcel(String datasource, SqlAndParams sqlAndParams, Pageable pageable,
                               HttpServletResponse response) {
        try {
            String pageSql = sqlAndParams.getSql();
            if (pageable.getSort().isSorted()) {
                pageSql = RegularSqlParseUtil.removeOrderByClause(pageSql);
                pageSql += " ORDER BY " + RegularSqlParseUtil.buildOrderByClause(pageable.getSort());
            }

            String finalPageSql = pageSql;
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");

            dynamicDataSourceService.execute(datasource, jt -> {
                return jt.query(finalPageSql, sqlAndParams.getParams().toArray(), rs -> {
                    com.alibaba.excel.ExcelWriter excelWriter = null;
                    try {
                        excelWriter = com.alibaba.excel.EasyExcel.write(response.getOutputStream()).build();
                        com.alibaba.excel.write.metadata.WriteSheet writeSheet = com.alibaba.excel.EasyExcel.writerSheet("数据").build();

                        java.sql.ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        List<List<String>> head = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            List<String> headColumn = new ArrayList<>();
                            headColumn.add(StrUtil.toCamelCase(metaData.getColumnLabel(i)));
                            head.add(headColumn);
                        }
                        writeSheet.setHead(head);

                        List<List<Object>> dataList = new ArrayList<>(1000);
                        while (rs.next()) {
                            List<Object> row = new ArrayList<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.add(rs.getObject(i));
                            }
                            dataList.add(row);

                            if (dataList.size() >= 1000) {
                                excelWriter.write(dataList, writeSheet);
                                dataList.clear();
                            }
                        }
                        if (!dataList.isEmpty()) {
                            excelWriter.write(dataList, writeSheet);
                        }
                    } catch (Exception ex) {
                        throw new RuntimeException("Excel导出写入异常", ex);
                    } finally {
                        if (excelWriter != null) {
                            excelWriter.finish();
                        }
                    }
                    return null;
                });
            });
        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败", e);
        }
    }
}
