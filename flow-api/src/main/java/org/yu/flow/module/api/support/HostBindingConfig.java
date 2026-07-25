package org.yu.flow.module.api.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.yu.flow.util.FlowObjectMapperUtil;

/**
 * WRAP 宿主绑定配置（存于 {@code host_binding} / 发布快照）。
 */
@Data
public class HostBindingConfig {

    public static final String FORWARD_LOCAL = "LOCAL";
    /** 全量（在 logEnabled=true 时） */
    public static final String LOG_ALL = "ALL";
    /** 仅失败 */
    public static final String LOG_ERROR_ONLY = "ERROR_ONLY";
    /** 采样（成功按 samplePermille，失败始终记） */
    public static final String LOG_SAMPLE = "SAMPLE";

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    /** LOCAL（默认）；HTTP 预留 */
    private String forward = FORWARD_LOCAL;
    /** 转发目标 path，空则用接口 url */
    private String targetPath;
    /** 是否启用主动探活 */
    private boolean probeEnabled;
    /** 探活 path，空则用 targetPath/url */
    private String probePath;
    private String probeMethod = "GET";
    private int probeExpectStatus = 200;
    /** ALL / ERROR_ONLY / SAMPLE */
    private String logMode = LOG_ALL;
    /** SAMPLE 时成功请求千分比，0–1000，默认 100 */
    private int logSamplePermille = 100;

    public static HostBindingConfig parse(String json) {
        HostBindingConfig cfg = new HostBindingConfig();
        if (StrUtil.isBlank(json)) {
            return cfg;
        }
        try {
            JsonNode n = MAPPER.readTree(json);
            if (n.hasNonNull("forward")) {
                cfg.setForward(n.get("forward").asText(FORWARD_LOCAL));
            }
            if (n.has("targetPath") && !n.get("targetPath").isNull()) {
                cfg.setTargetPath(blankToNull(n.get("targetPath").asText(null)));
            }
            if (n.has("probeEnabled")) {
                cfg.setProbeEnabled(n.get("probeEnabled").asBoolean(false));
            }
            if (n.has("probePath") && !n.get("probePath").isNull()) {
                cfg.setProbePath(blankToNull(n.get("probePath").asText(null)));
            }
            if (n.hasNonNull("probeMethod")) {
                cfg.setProbeMethod(n.get("probeMethod").asText("GET"));
            }
            if (n.has("probeExpectStatus") && n.get("probeExpectStatus").canConvertToInt()) {
                cfg.setProbeExpectStatus(n.get("probeExpectStatus").asInt(200));
            }
            if (n.hasNonNull("logMode")) {
                cfg.setLogMode(normalizeLogMode(n.get("logMode").asText(LOG_ALL)));
            }
            if (n.has("logSamplePermille") && n.get("logSamplePermille").canConvertToInt()) {
                int v = n.get("logSamplePermille").asInt(100);
                cfg.setLogSamplePermille(Math.max(0, Math.min(1000, v)));
            }
        } catch (Exception ignored) {
            // 非法 JSON → 默认配置
        }
        return cfg;
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{\"forward\":\"LOCAL\"}";
        }
    }

    public static String normalizeLogMode(String raw) {
        if (StrUtil.isBlank(raw)) {
            return LOG_ALL;
        }
        String v = raw.trim().toUpperCase();
        if (LOG_ERROR_ONLY.equals(v) || LOG_SAMPLE.equals(v)) {
            return v;
        }
        return LOG_ALL;
    }

    /**
     * 是否应写执行日志（已假定接口 logEnabled=true）。
     */
    public boolean shouldLog(boolean success) {
        String mode = normalizeLogMode(logMode);
        if (LOG_ERROR_ONLY.equals(mode)) {
            return !success;
        }
        if (LOG_SAMPLE.equals(mode)) {
            if (!success) {
                return true;
            }
            int p = Math.max(0, Math.min(1000, logSamplePermille));
            if (p >= 1000) {
                return true;
            }
            if (p <= 0) {
                return false;
            }
            return (System.nanoTime() % 1000L) < p;
        }
        return true;
    }

    public String resolveProbePath(String apiUrl) {
        if (StrUtil.isNotBlank(probePath)) {
            return probePath.trim();
        }
        if (StrUtil.isNotBlank(targetPath)) {
            return targetPath.trim();
        }
        return StrUtil.blankToDefault(apiUrl, "").trim();
    }

    private static String blankToNull(String v) {
        return StrUtil.isBlank(v) ? null : v.trim();
    }
}
