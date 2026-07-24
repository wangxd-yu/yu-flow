package org.yu.flow.module.api.security;

/**
 * 合并全局默认与接口覆盖后的不可变入站防护生效配置。
 */
public final class EffectiveSecurity {

    private final IngressAuthMode authMode;
    private final boolean antiReplay;
    private final boolean rateLimitEnabled;
    private final int rateLimitQps;
    /** 空串表示不限制 */
    private final String ipAllowlist;
    /** ≤0 表示不限制执行时间 */
    private final int timeoutMs;

    public EffectiveSecurity(IngressAuthMode authMode, boolean antiReplay, boolean rateLimitEnabled,
                             int rateLimitQps, String ipAllowlist, int timeoutMs) {
        this.authMode = authMode == null ? IngressAuthMode.NONE : authMode;
        this.antiReplay = antiReplay;
        this.rateLimitEnabled = rateLimitEnabled;
        this.rateLimitQps = Math.max(0, rateLimitQps);
        this.ipAllowlist = ipAllowlist == null ? "" : ipAllowlist;
        this.timeoutMs = timeoutMs;
    }

    public IngressAuthMode getAuthMode() {
        return authMode;
    }

    public boolean isAntiReplay() {
        return antiReplay;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public int getRateLimitQps() {
        return rateLimitQps;
    }

    public String getIpAllowlist() {
        return ipAllowlist;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    /** 信任宿主：无鉴权、无限流、无 IP 限制；超时仍可配置 */
    public static EffectiveSecurity trustHost(int timeoutMs) {
        return new EffectiveSecurity(IngressAuthMode.NONE, false, false, 0, "", timeoutMs);
    }
}
