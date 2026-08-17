package org.yu.flow.module.api.security;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yu.flow.module.api.domain.FlowApiDO;
import org.yu.flow.module.api.support.PublishedApiSnapshot;
import org.yu.flow.module.directory.service.FlowDirectoryService;
import org.yu.flow.module.host.CallerPolicy;
import org.yu.flow.module.sysconfig.support.YuFlowRuntimeSettings;
import org.yu.flow.util.FlowObjectMapperUtil;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 合并入站防护：
 * <pre>
 * 接口 securityConfig（字段显式值）
 *   → 目录链（所属目录起沿 parent 向上，字段级）
 *   → 全局 yu.flow.ingress / 系统配置
 * </pre>
 * <p>目录配置在库表上变更后，对「接口未覆盖」的字段<strong>即时生效</strong>（无需重新发布接口）。</p>
 */
@Slf4j
@Component
public class IngressSecurityResolver {

    private static final ObjectMapper MAPPER = FlowObjectMapperUtil.flowObjectMapper();
    private static final Cache<String, ApiSecurityConfig> CONFIG_CACHE = Caffeine.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    @Resource
    private YuFlowRuntimeSettings yuFlowRuntimeSettings;

    @Resource
    private FlowDirectoryService flowDirectoryService;

    public EffectiveSecurity resolve(FlowApiDO api) {
        ApiSecurityConfig apiCfg = parseConfig(PublishedApiSnapshot.resolveSecurityConfig(api));
        ApiSecurityConfig dirCfg = flowDirectoryService != null
                ? flowDirectoryService.resolveDirectorySecurityOverrides(api != null ? api.getDirectoryId() : null)
                : new ApiSecurityConfig();

        int timeoutMs = firstNonNull(apiCfg.getTimeoutMs(),
                dirCfg != null ? dirCfg.getTimeoutMs() : null,
                yuFlowRuntimeSettings.getIngressDefaultTimeoutMs());

        if (!yuFlowRuntimeSettings.isIngressEnabled()) {
            return EffectiveSecurity.trustHost(timeoutMs);
        }

        IngressAuthMode authMode = resolveAuthMode(apiCfg, dirCfg);

        boolean antiReplay = firstNonNull(apiCfg.getAntiReplay(),
                dirCfg != null ? dirCfg.getAntiReplay() : null,
                yuFlowRuntimeSettings.isIngressDefaultAntiReplay());

        boolean rateLimitEnabled = firstNonNull(apiCfg.getRateLimitEnabled(),
                dirCfg != null ? dirCfg.getRateLimitEnabled() : null,
                yuFlowRuntimeSettings.isIngressDefaultRateLimitEnabled());

        int rateLimitQps = firstNonNull(apiCfg.getRateLimitQps(),
                dirCfg != null ? dirCfg.getRateLimitQps() : null,
                yuFlowRuntimeSettings.getIngressDefaultRateLimitQps());

        String ipAllowlist;
        if (apiCfg.getIpAllowlist() != null) {
            ipAllowlist = apiCfg.getIpAllowlist();
        } else if (dirCfg != null && dirCfg.getIpAllowlist() != null) {
            ipAllowlist = dirCfg.getIpAllowlist();
        } else {
            ipAllowlist = StrUtil.nullToEmpty(yuFlowRuntimeSettings.getIngressDefaultIpAllowlist());
        }

        return new EffectiveSecurity(authMode, antiReplay, rateLimitEnabled, rateLimitQps, ipAllowlist, timeoutMs,
                resolveCallerPolicy(apiCfg, dirCfg));
    }

    /**
     * 接口 {@code callerPolicy.enabled=true} 优先；否则用目录链已合并的策略（含父目录）。
     */
    private static CallerPolicy resolveCallerPolicy(ApiSecurityConfig apiCfg, ApiSecurityConfig dirCfg) {
        CallerPolicy api = apiCfg != null ? apiCfg.getCallerPolicy() : null;
        if (api != null && api.isEnabled()) {
            return api;
        }
        if (dirCfg != null && dirCfg.getCallerPolicy() != null) {
            return dirCfg.getCallerPolicy();
        }
        return api;
    }

    private IngressAuthMode resolveAuthMode(ApiSecurityConfig apiCfg, ApiSecurityConfig dirCfg) {
        String modeRaw = apiCfg.getAuthMode();
        if (StrUtil.isNotBlank(modeRaw) && !"INHERIT".equalsIgnoreCase(modeRaw.trim())) {
            return IngressAuthMode.from(modeRaw);
        }
        if (dirCfg != null && StrUtil.isNotBlank(dirCfg.getAuthMode())
                && !"INHERIT".equalsIgnoreCase(dirCfg.getAuthMode().trim())) {
            return IngressAuthMode.from(dirCfg.getAuthMode());
        }
        return IngressAuthMode.from(yuFlowRuntimeSettings.getIngressDefaultAuthMode());
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public ApiSecurityConfig parseConfig(String json) {
        if (StrUtil.isBlank(json)) {
            ApiSecurityConfig empty = new ApiSecurityConfig();
            empty.setAuthMode("INHERIT");
            return empty;
        }
        return CONFIG_CACHE.get(json, IngressSecurityResolver::parseConfigJson);
    }

    private static ApiSecurityConfig parseConfigJson(String json) {
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
            // 损坏配置也缓存为全继承，避免每个请求重复解析并刷日志。
            log.warn("[IngressSecurityResolver] 解析 securityConfig 失败: {}", e.getMessage());
            return new ApiSecurityConfig();
        }
    }
}
