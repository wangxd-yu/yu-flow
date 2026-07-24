package org.yu.flow.module.api.security;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;

/**
 * 合并入站防护全局默认（系统配置 &gt; yml）与接口 {@code securityConfig}，产出 {@link EffectiveSecurity}。
 */
@Slf4j
@Component
public class IngressSecurityResolver {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();

    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;

    public EffectiveSecurity resolve(FlowApiDO api) {
        ApiSecurityConfig override = parseConfig(PublishedApiSnapshot.resolveSecurityConfig(api));
        int timeoutMs = resolveTimeoutMs(override);

        if (!yuFlowRuntimeSettings.isIngressEnabled()) {
            return EffectiveSecurity.trustHost(timeoutMs);
        }

        IngressAuthMode authMode;
        String modeRaw = override.getAuthMode();
        if (StrUtil.isBlank(modeRaw) || "INHERIT".equalsIgnoreCase(modeRaw.trim())) {
            authMode = IngressAuthMode.from(yuFlowRuntimeSettings.getIngressDefaultAuthMode());
        } else {
            authMode = IngressAuthMode.from(modeRaw);
        }

        boolean antiReplay = override.getAntiReplay() != null
                ? override.getAntiReplay()
                : yuFlowRuntimeSettings.isIngressDefaultAntiReplay();

        boolean rateLimitEnabled = override.getRateLimitEnabled() != null
                ? override.getRateLimitEnabled()
                : yuFlowRuntimeSettings.isIngressDefaultRateLimitEnabled();

        int rateLimitQps = override.getRateLimitQps() != null
                ? override.getRateLimitQps()
                : yuFlowRuntimeSettings.getIngressDefaultRateLimitQps();

        String ipAllowlist;
        if (override.getIpAllowlist() != null) {
            ipAllowlist = override.getIpAllowlist();
        } else {
            ipAllowlist = StrUtil.nullToEmpty(yuFlowRuntimeSettings.getIngressDefaultIpAllowlist());
        }

        return new EffectiveSecurity(authMode, antiReplay, rateLimitEnabled, rateLimitQps, ipAllowlist, timeoutMs);
    }

    private int resolveTimeoutMs(ApiSecurityConfig override) {
        if (override.getTimeoutMs() != null) {
            return override.getTimeoutMs();
        }
        return yuFlowRuntimeSettings.getIngressDefaultTimeoutMs();
    }

    public ApiSecurityConfig parseConfig(String json) {
        if (StrUtil.isBlank(json)) {
            ApiSecurityConfig empty = new ApiSecurityConfig();
            empty.setAuthMode("INHERIT");
            return empty;
        }
        try {
            ApiSecurityConfig cfg = MAPPER.readValue(json, ApiSecurityConfig.class);
            if (cfg == null) {
                return new ApiSecurityConfig();
            }
            if (StrUtil.isBlank(cfg.getAuthMode())) {
                cfg.setAuthMode("INHERIT");
            }
            return cfg;
        } catch (Exception e) {
            log.warn("[IngressSecurityResolver] 解析 securityConfig 失败: {}", e.getMessage());
            return new ApiSecurityConfig();
        }
    }
}
