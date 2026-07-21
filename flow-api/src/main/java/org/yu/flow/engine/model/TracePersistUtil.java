package org.yu.flow.engine.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.util.ContentHashUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 落库前处理：DSL 改为内容哈希引用、可选仅保留失败步 I/O、超限截断。
 *
 * <p>多节点共享 DB，本工具无状态；不依赖 Redis。</p>
 */
@Slf4j
public final class TracePersistUtil {

    private TracePersistUtil() {
    }

    public record PersistOptions(boolean failedStepsOnly, int maxBytes) {
        public static PersistOptions from(YuFlowProperties.Engine engine) {
            if (engine == null) {
                return new PersistOptions(false, 512 * 1024);
            }
            return new PersistOptions(
                    engine.isTracePersistFailedStepsOnly(),
                    Math.max(16 * 1024, engine.getTraceDataMaxBytes())
            );
        }

        public static final PersistOptions DEFAULTS = new PersistOptions(false, 512 * 1024);
    }

    /**
     * 准备落库：写入 {@code dslContentHash}，清空全文 {@code dslSnapshot}；
     * 可选剥离成功步的 inputs/outputs。
     */
    public static void prepareForPersist(FlowTrace trace, String dslContent, PersistOptions options) {
        if (trace == null) {
            return;
        }
        PersistOptions opts = options != null ? options : PersistOptions.DEFAULTS;
        if (dslContent != null && !dslContent.isBlank()) {
            trace.setDslContentHash(ContentHashUtil.sha256Hex(dslContent));
        }
        // 不再内嵌全文 DSL，回放前端回退加载当前资产草稿/发布内容
        trace.setDslSnapshot(null);

        if (opts.failedStepsOnly() && trace.getStepLogs() != null) {
            for (ExecutionLog stepLog : trace.getStepLogs()) {
                if (stepLog == null) {
                    continue;
                }
                String status = stepLog.getStatus();
                if (status == null || !"error".equalsIgnoreCase(status)) {
                    stepLog.setInputs(null);
                    stepLog.setOutputs(null);
                }
            }
        }
    }

    /**
     * 序列化并在超限时递进收缩，保证 DB 行可控。
     */
    public static String serializeForPersist(
            FlowTrace trace,
            String dslContent,
            ObjectMapper mapper,
            PersistOptions options) {
        if (trace == null || mapper == null) {
            return null;
        }
        PersistOptions opts = options != null ? options : PersistOptions.DEFAULTS;
        prepareForPersist(trace, dslContent, opts);

        try {
            String json = mapper.writeValueAsString(trace);
            if (byteLength(json) <= opts.maxBytes()) {
                return json;
            }

            // 1) 强制剥离所有成功步 I/O
            stripSuccessStepIo(trace);
            json = mapper.writeValueAsString(trace);
            if (byteLength(json) <= opts.maxBytes()) {
                log.warn("[TracePersist] 超限后剥离成功步 I/O，bytes≈{}", byteLength(json));
                return json;
            }

            // 2) 仅保留失败 / error 步
            keepErrorStepsOnly(trace);
            json = mapper.writeValueAsString(trace);
            if (byteLength(json) <= opts.maxBytes()) {
                log.warn("[TracePersist] 超限后仅保留失败步，bytes≈{}", byteLength(json));
                return json;
            }

            // 3) 清空全局入出参
            trace.setGlobalInputs(null);
            trace.setGlobalOutputs(summarizeOutput(trace.getGlobalOutputs()));
            json = mapper.writeValueAsString(trace);
            if (byteLength(json) <= opts.maxBytes()) {
                log.warn("[TracePersist] 超限后清空全局 I/O，bytes≈{}", byteLength(json));
                return json;
            }

            // 4) 硬兜底：极简摘要
            Map<String, Object> stub = new HashMap<>();
            stub.put("traceId", trace.getTraceId());
            stub.put("status", trace.getStatus());
            stub.put("errorMsg", truncateStr(trace.getErrorMsg(), 2000));
            stub.put("dslContentHash", trace.getDslContentHash());
            stub.put("totalDurationMs", trace.getTotalDurationMs());
            stub.put("_truncated", true);
            stub.put("_originalBytes", byteLength(json));
            String stubJson = mapper.writeValueAsString(stub);
            log.warn("[TracePersist] 使用极简 stub 落库，originalBytes≈{}", byteLength(json));
            return stubJson;
        } catch (Exception e) {
            log.error("[TracePersist] 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private static void stripSuccessStepIo(FlowTrace trace) {
        if (trace.getStepLogs() == null) {
            return;
        }
        for (ExecutionLog stepLog : trace.getStepLogs()) {
            if (stepLog == null) {
                continue;
            }
            if (stepLog.getStatus() == null || !"error".equalsIgnoreCase(stepLog.getStatus())) {
                stepLog.setInputs(null);
                stepLog.setOutputs(null);
            }
        }
    }

    private static void keepErrorStepsOnly(FlowTrace trace) {
        if (trace.getStepLogs() == null) {
            return;
        }
        List<ExecutionLog> kept = new ArrayList<>();
        for (ExecutionLog stepLog : trace.getStepLogs()) {
            if (stepLog != null && "error".equalsIgnoreCase(stepLog.getStatus())) {
                kept.add(stepLog);
            }
        }
        trace.setStepLogs(kept);
    }

    private static Object summarizeOutput(Object outputs) {
        if (outputs == null) {
            return null;
        }
        return outputs.getClass().getSimpleName() + "(omitted)";
    }

    private static String truncateStr(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static int byteLength(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }
}
