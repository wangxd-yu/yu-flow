package org.yu.flow.module.sysconfig.support;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import org.yu.flow.config.YuFlowProperties;
import org.yu.flow.module.sysconfig.cache.SysConfigCacheManager;
import org.yu.flow.module.sysconfig.domain.SysConfigDO;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 开放平台 / 入站防护运行时配置桥接。
 *
 * <p>优先级：{@code flow_sys_config}（启用且在缓存中）→ {@code yu.flow.*}（yml/环境变量）→ 代码默认。</p>
 * <p>库中无键或已停用时回退 yml，保证初始化阶段无系统配置也能工作。</p>
 * <p>{@code open.entry-prefix} 仅走 yml（影响路由契约，不热更）。</p>
 */
@Component
public class YuFlowRuntimeSettings {

    /** 配置键常量（与 Flyway seed 一致） */
    public static final class Keys {
        private Keys() {
        }

        public static final String OPEN_ENABLED = "OPEN_ENABLED";
        public static final String OPEN_ALLOW_PLAIN_SECRET = "OPEN_ALLOW_PLAIN_SECRET";
        public static final String OPEN_ALLOW_DIRECT_PATH = "OPEN_ALLOW_DIRECT_PATH";
        public static final String OPEN_REQUIRE_HOST_AUTH = "OPEN_REQUIRE_HOST_AUTH";
        public static final String OPEN_CALL_LOG_ENABLED = "OPEN_CALL_LOG_ENABLED";
        public static final String OPEN_SKEW_SECONDS = "OPEN_SKEW_SECONDS";
        public static final String OPEN_NONCE_FAIL_CLOSED = "OPEN_NONCE_FAIL_CLOSED";
        public static final String OPEN_INCLUDE_BODY_HASH = "OPEN_INCLUDE_BODY_HASH";
        public static final String OPEN_ROTATE_GRACE_HOURS = "OPEN_ROTATE_GRACE_HOURS";

        public static final String INGRESS_ENABLED = "INGRESS_ENABLED";
        public static final String INGRESS_DEFAULT_AUTH_MODE = "INGRESS_DEFAULT_AUTH_MODE";
        public static final String INGRESS_DEFAULT_ANTI_REPLAY = "INGRESS_DEFAULT_ANTI_REPLAY";
        public static final String INGRESS_DEFAULT_RATE_LIMIT_ENABLED = "INGRESS_DEFAULT_RATE_LIMIT_ENABLED";
        public static final String INGRESS_DEFAULT_RATE_LIMIT_QPS = "INGRESS_DEFAULT_RATE_LIMIT_QPS";
        public static final String INGRESS_DEFAULT_IP_ALLOWLIST = "INGRESS_DEFAULT_IP_ALLOWLIST";
        public static final String INGRESS_RATE_LIMIT_FAIL_OPEN = "INGRESS_RATE_LIMIT_FAIL_OPEN";
        public static final String INGRESS_DEFAULT_TIMEOUT_MS = "INGRESS_DEFAULT_TIMEOUT_MS";

        public static final String SCRIPT_ALLOWED_LANGUAGES = "SCRIPT_ALLOWED_LANGUAGES";
        public static final String ENGINE_DEFAULT_LOG_MODE = "ENGINE_DEFAULT_LOG_MODE";
        public static final String MQ_LOG_PAYLOAD_MODE = "MQ_LOG_PAYLOAD_MODE";
    }

    @Resource
    private YuFlowProperties yuFlowProperties;
    @Resource
    private SysConfigCacheManager sysConfigCacheManager;

    // ── Engine ──

    public String getEngineDefaultLogMode() {
        String ymlDefault = yuFlowProperties != null && yuFlowProperties.getEngine() != null
                ? yuFlowProperties.getEngine().getDefaultLogMode() : "ERROR_ONLY";
        return resolveString(Keys.ENGINE_DEFAULT_LOG_MODE, ymlDefault);
    }

    public String getMqLogPayloadMode() {
        String ymlDefault = yuFlowProperties != null && yuFlowProperties.getMq() != null
                ? yuFlowProperties.getMq().getLogPayloadMode() : "FULL";
        return resolveString(Keys.MQ_LOG_PAYLOAD_MODE, ymlDefault);
    }

    // ── Open ──

    public boolean isOpenEnabled() {
        return resolveBool(Keys.OPEN_ENABLED, openYml().isEnabled());
    }

    /** 路由前缀仅 yml，不读系统配置 */
    public String getOpenEntryPrefix() {
        return openYml().getEntryPrefix();
    }

    public boolean isOpenAllowPlainSecret() {
        return resolveBool(Keys.OPEN_ALLOW_PLAIN_SECRET, openYml().isAllowPlainSecret());
    }

    public boolean isOpenAllowDirectPath() {
        return resolveBool(Keys.OPEN_ALLOW_DIRECT_PATH, openYml().isAllowDirectPath());
    }

    public boolean isOpenRequireHostAuth() {
        return resolveBool(Keys.OPEN_REQUIRE_HOST_AUTH, openYml().isRequireHostAuth());
    }

    public boolean isOpenCallLogEnabled() {
        return resolveBool(Keys.OPEN_CALL_LOG_ENABLED, openYml().isCallLogEnabled());
    }

    public int getOpenSkewSeconds() {
        return resolveInt(Keys.OPEN_SKEW_SECONDS, openYml().getSkewSeconds());
    }

    public boolean isOpenNonceFailClosed() {
        return resolveBool(Keys.OPEN_NONCE_FAIL_CLOSED, openYml().isNonceFailClosed());
    }

    public boolean isOpenIncludeBodyHash() {
        return resolveBool(Keys.OPEN_INCLUDE_BODY_HASH, openYml().isIncludeBodyHash());
    }

    public int getOpenRotateGraceHours() {
        return resolveInt(Keys.OPEN_ROTATE_GRACE_HOURS, openYml().getRotateGraceHours());
    }

    /**
     * 供鉴权链路使用的 Open 视图：热字段来自系统配置，前缀仍来自 yml。
     */
    public YuFlowProperties.Open resolveOpen() {
        YuFlowProperties.Open o = new YuFlowProperties.Open();
        o.setEnabled(isOpenEnabled());
        o.setEntryPrefix(getOpenEntryPrefix());
        o.setAllowPlainSecret(isOpenAllowPlainSecret());
        o.setAllowDirectPath(isOpenAllowDirectPath());
        o.setRequireHostAuth(isOpenRequireHostAuth());
        o.setCallLogEnabled(isOpenCallLogEnabled());
        o.setSkewSeconds(getOpenSkewSeconds());
        o.setNonceFailClosed(isOpenNonceFailClosed());
        o.setIncludeBodyHash(isOpenIncludeBodyHash());
        o.setRotateGraceHours(getOpenRotateGraceHours());
        return o;
    }

    // ── Ingress ──

    public boolean isIngressEnabled() {
        return resolveBool(Keys.INGRESS_ENABLED, ingressYml().isEnabled());
    }

    /**
     * 是否允许运行时/配置将入站鉴权设为 NONE。仅 yml/环境变量，不走系统配置热更。
     */
    public boolean isAllowIngressAuthNone() {
        YuFlowProperties.Security sec = yuFlowProperties.getSecurity();
        return sec != null && sec.isAllowIngressAuthNone();
    }

    public String getIngressDefaultAuthMode() {
        return resolveString(Keys.INGRESS_DEFAULT_AUTH_MODE, ingressYml().getDefaultAuthMode());
    }

    public boolean isIngressDefaultAntiReplay() {
        return resolveBool(Keys.INGRESS_DEFAULT_ANTI_REPLAY, ingressYml().isDefaultAntiReplay());
    }

    public boolean isIngressDefaultRateLimitEnabled() {
        return resolveBool(Keys.INGRESS_DEFAULT_RATE_LIMIT_ENABLED, ingressYml().isDefaultRateLimitEnabled());
    }

    public int getIngressDefaultRateLimitQps() {
        return resolveInt(Keys.INGRESS_DEFAULT_RATE_LIMIT_QPS, ingressYml().getDefaultRateLimitQps());
    }

    public String getIngressDefaultIpAllowlist() {
        return resolveString(Keys.INGRESS_DEFAULT_IP_ALLOWLIST,
                StrUtil.nullToEmpty(ingressYml().getDefaultIpAllowlist()));
    }

    public boolean isIngressRateLimitFailOpen() {
        return resolveBool(Keys.INGRESS_RATE_LIMIT_FAIL_OPEN, ingressYml().isRateLimitFailOpen());
    }

    /** 默认接口执行超时（毫秒）；≤0 不限制 */
    public int getIngressDefaultTimeoutMs() {
        return resolveInt(Keys.INGRESS_DEFAULT_TIMEOUT_MS, ingressYml().getDefaultTimeoutMs());
    }

    // ── Security ──

    /**
     * Evaluate / Switch 等节点允许的脚本语言白名单（逗号分隔）。
     * <p>sysconfig 优先级高于 yml；空串或不填则回退 yml 默认值。</p>
     */
    public List<String> getScriptAllowedLanguages() {
        return resolveStringList(Keys.SCRIPT_ALLOWED_LANGUAGES,
                yuFlowProperties != null && yuFlowProperties.getSecurity() != null
                        ? yuFlowProperties.getSecurity().getScriptAllowedLanguages()
                        : null);
    }

    // ── resolve helpers ──

    private boolean resolveBool(String key, boolean ymlDefault) {
        Optional<SysConfigDO> cfg = lookup(key);
        if (cfg.isEmpty()) {
            return ymlDefault;
        }
        return Convert.toBool(cfg.get().getConfigValue(), ymlDefault);
    }

    private int resolveInt(String key, int ymlDefault) {
        Optional<SysConfigDO> cfg = lookup(key);
        if (cfg.isEmpty()) {
            return ymlDefault;
        }
        Integer v = Convert.toInt(cfg.get().getConfigValue(), ymlDefault);
        return v != null ? v : ymlDefault;
    }

    private String resolveString(String key, String ymlDefault) {
        Optional<SysConfigDO> cfg = lookup(key);
        if (cfg.isEmpty()) {
            return ymlDefault;
        }
        // 允许空串（如 IP 白名单明确不限制）
        String v = cfg.get().getConfigValue();
        return v != null ? v : ymlDefault;
    }

    private List<String> resolveStringList(String key, List<String> ymlDefault) {
        Optional<SysConfigDO> cfg = lookup(key);
        if (cfg.isEmpty()) {
            return ymlDefault;
        }
        String v = cfg.get().getConfigValue();
        if (v == null) {
            return ymlDefault;
        }
        return Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private Optional<SysConfigDO> lookup(String key) {
        if (sysConfigCacheManager == null || StrUtil.isBlank(key)) {
            return Optional.empty();
        }
        return sysConfigCacheManager.getConfig(key);
    }

    private YuFlowProperties.Open openYml() {
        YuFlowProperties.Open open = yuFlowProperties != null ? yuFlowProperties.getOpen() : null;
        return open != null ? open : new YuFlowProperties.Open();
    }

    private YuFlowProperties.Ingress ingressYml() {
        YuFlowProperties.Ingress ingress = yuFlowProperties != null ? yuFlowProperties.getIngress() : null;
        return ingress != null ? ingress : new YuFlowProperties.Ingress();
    }
}
