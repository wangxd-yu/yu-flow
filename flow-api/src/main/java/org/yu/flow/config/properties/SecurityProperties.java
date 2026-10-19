package org.yu.flow.config.properties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全与加密相关配置。
 *
 * <p>对应 YAML 路径：{@code yu.flow.security.*}</p>
 */
public class SecurityProperties {

    /**
     * AES 对称加密密钥。
     * <p>用于数据源密码等敏感信息的加解密，长度必须为 16 / 24 / 32 字节。</p>
     * <p><b>生产环境强烈建议通过环境变量注入：</b></p>
     * <pre>
     * yu:
     *   flow:
     *     security:
     *       aes-secret-key: ${YU_FLOW_AES_SECRET:flow-secure-keys}
     * </pre>
     */
    private String aesSecretKey = "flow-secure-keys";

    /**
     * 管理端 JWT HMAC 密钥。
     * <p>生产必须通过 {@code YU_FLOW_JWT_SECRET} 覆盖，禁止使用历史默认值 {@code ss-flow-699}。</p>
     */
    private String jwtSecretKey = "ss-flow-699";

    /**
     * 管理端 JWT 有效期（秒），默认 2 小时。
     */
    private long jwtExpireSeconds = 7200L;

    /**
     * Groovy / 系统宏 SpEL 可通过 getBean / @bean 获取的 Bean 名称白名单。
     * 默认空列表；宏侧另内置允许 {@code environment}。
     */
    private List<String> scriptAllowedBeans = new ArrayList<>();

    /**
     * Evaluate / Switch 等节点允许使用的脚本语言白名单。
     * <p>默认 {@code aviator, spel, javascript}（保障预置演示流程可用）；
     * {@code groovy}、{@code python} 逃逸面较大，需显式加入白名单。
     * 置空列表表示不限制（不建议）。</p>
     */
    private List<String> scriptAllowedLanguages =
            new ArrayList<>(List.of("aviator", "spel", "javascript"));

    /**
     * 为 true 时：JWT/AES 仍为历史默认值则拒绝启动；弱默认管理员口令亦拒绝启动。
     * 本地开发可设 {@code YU_FLOW_FAIL_ON_INSECURE_DEFAULTS=false}。
     */
    private boolean failOnInsecureDefaults = true;

    /**
     * 出站 HTTP（流程 HttpRequest / Webhook）是否拒绝私网与环回地址。
     * 默认 false（内网集成常见）；公网多租户可设 true。
     */
    private boolean blockPrivateOutbound = true;

    /**
     * 是否允许 yml 账号在 RBAC 库用户鉴权失败后兜底登录（仅紧急运维）。
     * 生产建议 false。
     */
    private boolean allowYmlAdminFallback = false;

    /**
     * 是否允许接口 {@code securityConfig.authMode=NONE}（匿名可调业务 API）。
     * 默认 false；仅应急可设 {@code YU_FLOW_ALLOW_INGRESS_AUTH_NONE=true}。
     */
    private boolean allowIngressAuthNone = false;

    /**
     * 是否允许 HttpRequest 节点 {@code ignoreSsl=true}（跳过证书与主机名校验）。
     * <p>默认 true（内网自签名常见）；生产建议设
     * {@code YU_FLOW_ALLOW_IGNORE_SSL=false} 一刀切禁用。</p>
     */
    private boolean allowIgnoreSsl = true;

    /**
     * 本实例环境为 PROD 时，发现不安全档位仍允许启动（只打 ERROR 日志）。
     * <p>默认 false：PROD 下这些档位会让启动失败，系统配置与发布包也不能把它们改回不安全值。
     * 见 {@link org.yu.flow.config.ProdSecurityGuard}。</p>
     */
    private boolean allowInsecureProd = false;

    /**
     * 管理端 {@code /flow-api/**}（已通过管理端 JWT 后）是否再要求
     * {@link org.yu.flow.module.open.auth.HostAuthenticationProbe} 通过。
     * <p>默认 {@code false}；仅需加强防护时开启。开启后嵌入宿主应覆盖 Probe
     * 读自身 Session/SecurityContext，形成「宿主已登录 + Flow JWT」双层鉴权。</p>
     * <p>不覆盖 login / captcha / open / 签名下载。不做宿主登录后静默换发 Flow JWT。</p>
     */
    private boolean managementRequireHostAuth = false;

    /**
     * 登录口令 SM2 私钥（hex，D 值）。与 {@link #sm2PublicKey} 成对；
     * 均留空则进程启动时生成临时密钥（多节点须通过环境变量固定）。
     */
    private String sm2PrivateKey = "";

    /**
     * 登录口令 SM2 公钥（hex，未压缩 {@code 04||X||Y}，130 字符）。
     */
    private String sm2PublicKey = "";

    /**
     * 登录 SM2 密文有效期（秒）。载荷内时间戳超出此窗则拒绝。
     */
    private long sm2PasswordTtlSeconds = 300L;

    public String getAesSecretKey() {
        return aesSecretKey;
    }

    public void setAesSecretKey(String aesSecretKey) {
        this.aesSecretKey = aesSecretKey;
    }

    public String getJwtSecretKey() {
        return jwtSecretKey;
    }

    public void setJwtSecretKey(String jwtSecretKey) {
        this.jwtSecretKey = jwtSecretKey;
    }

    public long getJwtExpireSeconds() {
        return jwtExpireSeconds;
    }

    public void setJwtExpireSeconds(long jwtExpireSeconds) {
        this.jwtExpireSeconds = jwtExpireSeconds;
    }

    public List<String> getScriptAllowedBeans() {
        return scriptAllowedBeans;
    }

    public void setScriptAllowedBeans(List<String> scriptAllowedBeans) {
        this.scriptAllowedBeans = scriptAllowedBeans == null ? new ArrayList<>() : scriptAllowedBeans;
    }

    public List<String> getScriptAllowedLanguages() {
        return scriptAllowedLanguages;
    }

    public void setScriptAllowedLanguages(List<String> scriptAllowedLanguages) {
        this.scriptAllowedLanguages = scriptAllowedLanguages == null
                ? new ArrayList<>() : scriptAllowedLanguages;
    }

    public boolean isFailOnInsecureDefaults() {
        return failOnInsecureDefaults;
    }

    public void setFailOnInsecureDefaults(boolean failOnInsecureDefaults) {
        this.failOnInsecureDefaults = failOnInsecureDefaults;
    }

    public boolean isBlockPrivateOutbound() {
        return blockPrivateOutbound;
    }

    public void setBlockPrivateOutbound(boolean blockPrivateOutbound) {
        this.blockPrivateOutbound = blockPrivateOutbound;
    }

    public boolean isAllowYmlAdminFallback() {
        return allowYmlAdminFallback;
    }

    public void setAllowYmlAdminFallback(boolean allowYmlAdminFallback) {
        this.allowYmlAdminFallback = allowYmlAdminFallback;
    }

    public boolean isAllowIngressAuthNone() {
        return allowIngressAuthNone;
    }

    public void setAllowIngressAuthNone(boolean allowIngressAuthNone) {
        this.allowIngressAuthNone = allowIngressAuthNone;
    }

    public boolean isAllowIgnoreSsl() {
        return allowIgnoreSsl;
    }

    public void setAllowIgnoreSsl(boolean allowIgnoreSsl) {
        this.allowIgnoreSsl = allowIgnoreSsl;
    }

    public boolean isAllowInsecureProd() {
        return allowInsecureProd;
    }

    public void setAllowInsecureProd(boolean allowInsecureProd) {
        this.allowInsecureProd = allowInsecureProd;
    }

    public boolean isManagementRequireHostAuth() {
        return managementRequireHostAuth;
    }

    public void setManagementRequireHostAuth(boolean managementRequireHostAuth) {
        this.managementRequireHostAuth = managementRequireHostAuth;
    }

    public String getSm2PrivateKey() {
        return sm2PrivateKey;
    }

    public void setSm2PrivateKey(String sm2PrivateKey) {
        this.sm2PrivateKey = sm2PrivateKey;
    }

    public String getSm2PublicKey() {
        return sm2PublicKey;
    }

    public void setSm2PublicKey(String sm2PublicKey) {
        this.sm2PublicKey = sm2PublicKey;
    }

    public long getSm2PasswordTtlSeconds() {
        return sm2PasswordTtlSeconds;
    }

    public void setSm2PasswordTtlSeconds(long sm2PasswordTtlSeconds) {
        this.sm2PasswordTtlSeconds = sm2PasswordTtlSeconds;
    }
}
