package org.yu.flow.config.properties;

/**
 * 第三方开放平台配置。
 *
 * <p>对应 YAML：{@code yu.flow.open.*}</p>
 */
public class OpenProperties {

    /** 总开关；关闭后 /flow-api/open/** 返回 404 */
    private boolean enabled = true;

    /** 开放入口前缀（不含尾斜杠） */
    private String entryPrefix = "/flow-api/open";

    /** 签名时钟偏差秒数 */
    private int skewSeconds = 300;

    /** 是否允许 X-Yu-App-Secret 明文头（无签名时）；默认 false，演示可环境变量打开 */
    private boolean allowPlainSecret = false;

    /** 轮换后旧密钥宽限期（小时）；≤0 表示立即失效 */
    private int rotateGraceHours = 24;

    /** 全局入站摘要日志开关；平台 openCallLogEnabled=0 时可单独关闭 */
    private boolean callLogEnabled = true;

    /** HMAC 是否纳入 body SHA-256（空 body 用空串） */
    private boolean includeBodyHash = true;

    /**
     * nonce 写入 Redis 失败时是否拒绝请求。
     * 生产建议 true；本地无 Redis 时可 false（存在重放风险）。
     */
    private boolean nonceFailClosed = true;

    /**
     * 是否允许第三方用 AppKey 直打「真实发布 path」（凭证头分流）。
     * 默认 false：仅认 {@code /flow-api/open/{path}} 前缀入口。
     */
    private boolean allowDirectPath = false;

    /**
     * 已发布 API 且无 AppKey、且 ingress 关闭时，是否强制
     * {@link org.yu.flow.module.open.auth.HostAuthenticationProbe}。
     * 默认 true；独立部署默认 Probe 校验管理端 JWT。
     */
    private boolean requireHostAuth = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEntryPrefix() {
        return entryPrefix;
    }

    public void setEntryPrefix(String entryPrefix) {
        this.entryPrefix = entryPrefix;
    }

    public int getSkewSeconds() {
        return skewSeconds;
    }

    public void setSkewSeconds(int skewSeconds) {
        this.skewSeconds = skewSeconds;
    }

    public boolean isAllowPlainSecret() {
        return allowPlainSecret;
    }

    public void setAllowPlainSecret(boolean allowPlainSecret) {
        this.allowPlainSecret = allowPlainSecret;
    }

    public int getRotateGraceHours() {
        return rotateGraceHours;
    }

    public void setRotateGraceHours(int rotateGraceHours) {
        this.rotateGraceHours = rotateGraceHours;
    }

    public boolean isCallLogEnabled() {
        return callLogEnabled;
    }

    public void setCallLogEnabled(boolean callLogEnabled) {
        this.callLogEnabled = callLogEnabled;
    }

    public boolean isIncludeBodyHash() {
        return includeBodyHash;
    }

    public void setIncludeBodyHash(boolean includeBodyHash) {
        this.includeBodyHash = includeBodyHash;
    }

    public boolean isNonceFailClosed() {
        return nonceFailClosed;
    }

    public void setNonceFailClosed(boolean nonceFailClosed) {
        this.nonceFailClosed = nonceFailClosed;
    }

    public boolean isAllowDirectPath() {
        return allowDirectPath;
    }

    public void setAllowDirectPath(boolean allowDirectPath) {
        this.allowDirectPath = allowDirectPath;
    }

    public boolean isRequireHostAuth() {
        return requireHostAuth;
    }

    public void setRequireHostAuth(boolean requireHostAuth) {
        this.requireHostAuth = requireHostAuth;
    }
}
