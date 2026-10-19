package org.yu.flow.config.properties;

/**
 * 已发布 API 入站防护全局默认。
 *
 * <p>对应 YAML：{@code yu.flow.ingress.*}</p>
 * <p>{@code enabled=false} 时信任宿主网关（与历史行为一致）；开放入口 {@code /flow-api/open/**} 不受影响。</p>
 */
public class IngressProperties {

    /** 总开关；关闭则不启用入站兜底（此时网关仍会对已匹配动态 API 要求管理端 JWT） */
    private boolean enabled = true;

    /** 默认鉴权：NONE | HOST | OPEN（安全默认 HOST=需管理端登录态） */
    private String defaultAuthMode = "HOST";

    /** 默认是否启用防重放（仅 OPEN 鉴权生效） */
    private boolean defaultAntiReplay = true;

    /** 默认是否启用限流 */
    private boolean defaultRateLimitEnabled = false;

    /** 默认限流 QPS（秒级固定窗口） */
    private int defaultRateLimitQps = 100;

    /** 默认 IP 白名单（空=不限制） */
    private String defaultIpAllowlist = "";

    /**
     * 默认接口执行超时（毫秒）。≤0 表示不限制。
     * 可被接口 securityConfig.timeoutMs 覆盖。
     */
    private int defaultTimeoutMs = 30000;

    /** 限流 Redis 失败时是否 fail-open */
    private boolean rateLimitFailOpen = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultAuthMode() {
        return defaultAuthMode;
    }

    public void setDefaultAuthMode(String defaultAuthMode) {
        this.defaultAuthMode = defaultAuthMode;
    }

    public boolean isDefaultAntiReplay() {
        return defaultAntiReplay;
    }

    public void setDefaultAntiReplay(boolean defaultAntiReplay) {
        this.defaultAntiReplay = defaultAntiReplay;
    }

    public boolean isDefaultRateLimitEnabled() {
        return defaultRateLimitEnabled;
    }

    public void setDefaultRateLimitEnabled(boolean defaultRateLimitEnabled) {
        this.defaultRateLimitEnabled = defaultRateLimitEnabled;
    }

    public int getDefaultRateLimitQps() {
        return defaultRateLimitQps;
    }

    public void setDefaultRateLimitQps(int defaultRateLimitQps) {
        this.defaultRateLimitQps = defaultRateLimitQps;
    }

    public String getDefaultIpAllowlist() {
        return defaultIpAllowlist;
    }

    public void setDefaultIpAllowlist(String defaultIpAllowlist) {
        this.defaultIpAllowlist = defaultIpAllowlist;
    }

    public int getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(int defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public boolean isRateLimitFailOpen() {
        return rateLimitFailOpen;
    }

    public void setRateLimitFailOpen(boolean rateLimitFailOpen) {
        this.rateLimitFailOpen = rateLimitFailOpen;
    }
}
