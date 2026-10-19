package org.yu.flow.config.properties;

/**
 * 接口出站隐私拦截。对应 YAML：{@code yu.flow.privacy.*}
 */
public class PrivacyProperties {

    /**
     * 库内密文字段 SM4 密钥：16 字节明文或 32 位 hex。
     * 生产通过 {@code YU_FLOW_PRIVACY_AT_REST_SM4_KEY} 注入。
     */
    private String atRestSm4Key = "";

    /**
     * 已发布 JSON 接口的明文档是否再套传输 SM4（{@code X-Privacy-Key}）。
     * 运行时优先读系统参数 {@code PRIVACY_WRAP_TRANSPORT}，本字段仅作 yml 兜底。
     * {@code true}（默认）：无会话密钥则 REVEAL 降级为 MASK。
     * {@code false}：命中明文规则时响应里直接给明文，供内网或调试；生产不建议关。
     */
    private boolean wrapTransport = true;

    /**
     * 是否开放 {@code GET /flow-api/dev/privacy-session}（签发 {@code X-Privacy-Key} + {@code sm4KeyHex}）。
     * <p>生产 profile（{@code prod}/{@code production}）与 {@code demo-mode} 一律拒绝，忽略本项。
     * {@code dev}/{@code local}/{@code test} 即使本项为 {@code false} 也会开放。其它环境显式设
     * {@code YU_FLOW_PRIVACY_DEV_SESSION=true}。</p>
     */
    private boolean devSessionEnabled = false;

    public String getAtRestSm4Key() {
        return atRestSm4Key;
    }

    public void setAtRestSm4Key(String atRestSm4Key) {
        this.atRestSm4Key = atRestSm4Key;
    }

    public boolean isWrapTransport() {
        return wrapTransport;
    }

    public void setWrapTransport(boolean wrapTransport) {
        this.wrapTransport = wrapTransport;
    }

    public boolean isDevSessionEnabled() {
        return devSessionEnabled;
    }

    public void setDevSessionEnabled(boolean devSessionEnabled) {
        this.devSessionEnabled = devSessionEnabled;
    }
}
