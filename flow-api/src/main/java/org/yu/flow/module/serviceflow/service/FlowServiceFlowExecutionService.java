package org.yu.flow.module.serviceflow.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yu.flow.config.ContractParamTypeConverter;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.engine.evaluator.ExecutionResult;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowTrace;
import org.yu.flow.engine.model.TracePersistUtil;
import org.yu.flow.engine.log.LogMode;
import org.yu.flow.exception.FlowException;
import org.yu.flow.exception.SchemaValidationException;
import org.yu.flow.log.service.domain.FlowServiceLogDO;
import org.yu.flow.log.service.service.FlowServiceLogService;
import org.yu.flow.module.serviceflow.cache.ServiceResolvedContentCache;
import org.yu.flow.module.serviceflow.domain.FlowServiceFlowDO;
import org.yu.flow.module.serviceflow.repository.FlowServiceFlowRepository;
import org.yu.flow.module.metrics.AssetMetricsRecorder;
import org.yu.flow.module.metrics.MetricsAssetType;
import org.yu.flow.module.metrics.MetricsOutcome;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 内部服务编排执行入口（手动 / 流程内 CALL）。
 */
@Slf4j
@Service
public class FlowServiceFlowExecutionService {

    public static final int MAX_NEST_DEPTH = 8;

    private static final ThreadLocal<Integer> NEST_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ObjectMapper OBJECT_MAPPER = org.yu.flow.util.FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private FlowServiceFlowRepository flowServiceFlowRepository;

    @Resource
    private FlowEngine flowEngine;

    @Resource
    private FlowServiceLogService flowServiceLogService;

    @Resource
    private ContractParamTypeConverter contractParamTypeConverter;

    @Resource
    private YuFlowProperties yuFlowProperties;

    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;

    @Resource
    private ServiceResolvedContentCache serviceResolvedContentCache;

    @Resource
    private AssetMetricsRecorder assetMetricsRecorder;

    /**
     * 按服务 ID 执行，返回业务输出（解包 ExecutionResult / FlowTrace）。
     *
     * @param serviceId   服务 ID
     * @param input       调用方入参 → 写入 {@code $.service.input}
     * @param triggerType MANUAL / CALL / DEBUG
     * @param requireEnabled CALL 时必须启用；DEBUG/MANUAL 可运行草稿
     */
    public Object executeById(
            String serviceId,
            Map<String, Object> input,
            String triggerType,
            boolean requireEnabled
    ) {
        FlowServiceFlowDO svc = flowServiceFlowRepository.findById(serviceId).orElse(null);
        if (svc == null) {
            throw new FlowException("SERVICE_NOT_FOUND", "内部服务不存在: " + serviceId);
        }
        if (requireEnabled && !Boolean.TRUE.equals(svc.getEnabled())) {
            throw new FlowException("SERVICE_DISABLED", "内部服务已停用: " + svc.getName());
        }

        // CALL：必须已发布，走快照；MANUAL/DEBUG：走草稿便于联调
        boolean callMode = "CALL".equalsIgnoreCase(triggerType);
        if (callMode) {
            if (svc.getPublishStatus() == null || svc.getPublishStatus() != 1
                    || StrUtil.isBlank(svc.getPublishedSnapshot())) {
                throw new FlowException("SERVICE_NOT_PUBLISHED",
                        "内部服务未发布，无法被流程调用: " + svc.getName());
            }
        }

        ServiceResolvedContentCache.ResolvedContent resolved = resolveContent(svc, callMode);
        if (StrUtil.isBlank(resolved.dslContent())) {
            throw new FlowException("SERVICE_DSL_EMPTY", "内部服务 DSL 为空: " + svc.getName());
        }

        int depth = NEST_DEPTH.get();
        if (depth >= MAX_NEST_DEPTH) {
            throw new FlowException("SERVICE_NEST_TOO_DEEP",
                    "内部服务嵌套超过上限 " + MAX_NEST_DEPTH + "（当前深度 " + depth + "）");
        }

        NEST_DEPTH.set(depth + 1);
        long start = System.currentTimeMillis();
        String status = "RUNNING";
        String errorMsg = null;
        String traceData = null;
        Object businessOut = null;

        try {
            Map<String, Object> typedInput;
            try {
                typedInput = contractParamTypeConverter.convertAndValidateServiceInputs(
                        resolved.contract(), input);
            } catch (SchemaValidationException e) {
                throw new FlowException("SERVICE_INPUT_INVALID",
                        "内部服务入参校验失败 [" + svc.getName() + "]: " + e.getMessage(), e);
            }

            Map<String, Object> args = new HashMap<>();
            args.put("serviceName", svc.getName());
            args.put("serviceId", svc.getId());
            args.put("input", typedInput);

            String resolvedMode = resolveLogMode(svc);
            boolean traceEnabled = LogMode.ALL.equals(resolvedMode) || "DEBUG".equalsIgnoreCase(triggerType);
            Object result = flowEngine.execute(
                    resolved.dslContent(),
                    args,
                    traceEnabled,
                    "SERVICE",
                    svc.getId(),
                    svc.getName()
            );

            businessOut = unwrapBusinessOutput(result);
            if (result instanceof FlowTrace) {
                FlowTrace trace = (FlowTrace) result;
                if ("error".equalsIgnoreCase(trace.getStatus())) {
                    status = "FAILED";
                    errorMsg = trace.getErrorMsg();
                } else {
                    status = "SUCCESS";
                }
                boolean isSuccess = "SUCCESS".equals(status);
                if (LogMode.shouldRecordTrace(resolvedMode, isSuccess)) {
                    try {
                        TracePersistUtil.PersistOptions opts = TracePersistUtil.PersistOptions.from(
                                yuFlowProperties != null ? yuFlowProperties.getEngine() : null);
                        traceData = TracePersistUtil.serializeForPersist(
                                trace, resolved.dslContent(), OBJECT_MAPPER, opts);
                    } catch (Exception ignore) {
                        /* ignore */
                    }
                }
            } else if (result instanceof ExecutionResult) {
                ExecutionResult er = (ExecutionResult) result;
                status = er.isSuccess() ? "SUCCESS" : "FAILED";
                if (!er.isSuccess()) {
                    errorMsg = er.getMessage();
                }
            } else {
                status = "SUCCESS";
            }

            if ("FAILED".equals(status) && errorMsg != null) {
                throw new FlowException("SERVICE_EXEC_FAILED",
                        "内部服务执行失败 [" + svc.getName() + "]: " + errorMsg);
            }
            return businessOut;
        } catch (FlowException e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAILED";
            errorMsg = e.getMessage();
            throw new FlowException("SERVICE_EXEC_FAILED",
                    "内部服务执行失败 [" + svc.getName() + "]: " + e.getMessage(), e);
        } finally {
            NEST_DEPTH.set(depth);
            if (depth == 0) {
                NEST_DEPTH.remove();
            }
            long cost = System.currentTimeMillis() - start;
            String trig = triggerType != null ? triggerType : "MANUAL";
            MetricsOutcome outcome = "SUCCESS".equals(status)
                    ? MetricsOutcome.SUCCESS
                    : MetricsOutcome.FAIL;
            if (!"RUNNING".equals(status)) {
                assetMetricsRecorder.record(MetricsAssetType.SERVICE, svc.getId(), outcome, cost, trig);
            }
            String resolvedMode = resolveLogMode(svc);
            boolean isSuccess = "SUCCESS".equals(status);
            if (LogMode.shouldRecord(resolvedMode, isSuccess)) {
                try {
                    flowServiceLogService.saveAsync(FlowServiceLogDO.builder()
                            .serviceId(svc.getId())
                            .serviceName(svc.getName())
                            .triggerType(trig)
                            .status(status)
                            .costTimeMs(cost)
                            .errorMsg(errorMsg)
                            .traceData(traceData)
                            .build());
                } catch (Exception e) {
                    log.error("[ServiceFlow] 日志写入失败: serviceId={}, error={}", svc.getId(), e.getMessage());
                }
            }
        }
    }

    private String resolveLogMode(FlowServiceFlowDO svc) {
        String rawMode = svc != null ? svc.getLogMode() : null;
        String globalDefault = yuFlowRuntimeSettings != null
                ? yuFlowRuntimeSettings.getEngineDefaultLogMode()
                : (yuFlowProperties != null && yuFlowProperties.getEngine() != null
                        ? yuFlowProperties.getEngine().getDefaultLogMode() : null);
        return LogMode.resolve(rawMode, globalDefault);
    }

    private Object unwrapBusinessOutput(Object result) {
        if (result instanceof FlowTrace) {
            FlowTrace trace = (FlowTrace) result;
            Object outputs = trace.getGlobalOutputs();
            return outputs != null ? outputs : trace;
        }
        if (result instanceof ExecutionResult) {
            ExecutionResult er = (ExecutionResult) result;
            return er.getData() != null ? er.getData() : er;
        }
        return result;
    }

    /**
     * CALL 优先读 publishedSnapshot（内容哈希缓存）；其余走草稿字段。
     */
    private ServiceResolvedContentCache.ResolvedContent resolveContent(FlowServiceFlowDO svc, boolean usePublished) {
        if (usePublished && StrUtil.isNotBlank(svc.getPublishedSnapshot())) {
            ServiceResolvedContentCache.ResolvedContent cached =
                    serviceResolvedContentCache.getOrParsePublished(svc.getPublishedSnapshot());
            if (cached != null && StrUtil.isNotBlank(cached.dslContent())) {
                return cached;
            }
            log.warn("[ServiceFlow] 发布快照解析失败，降级草稿。serviceId={}", svc.getId());
        }
        return new ServiceResolvedContentCache.ResolvedContent(svc.getDslContent(), svc.getContract());
    }
}
