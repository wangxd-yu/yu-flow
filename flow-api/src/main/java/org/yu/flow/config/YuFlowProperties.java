package org.yu.flow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * yu-flow 引擎统一配置属性树。
 *
 * <p>所有配置项均收拢在 {@code yu.flow.*} 前缀下，通过内部静态类进行语义分组，
 * 确保在 IDE 中获得完整的自动补全与文档提示。</p>
 *
 * <h3>YAML 配置示例</h3>
 * <pre>
 * yu:
 *   flow:
 *     enabled: true
 *     username: admin
 *     password: flow@699
 *     enable-ui: true
 *     engine:
 *       expression-engine: simple
 *       strict-mode: true
 *       enable-trace: false
 *     security:
 *       aes-secret-key: flow-secure-keys
 * </pre>
 *
 * @author yu-flow
 * @since 1.0
 */
@ConfigurationProperties(prefix = "yu.flow")
public class YuFlowProperties {

    /**
     * 是否启用 yu-flow 引擎。
     * <p>设为 {@code false} 可完全禁用整个引擎的自动装配。</p>
     */
    private boolean enabled = true;

    /**
     * 是否启用内置管理 UI 界面。
     * <p>关闭后管理后台将返回 403，但对外发布的页面（preview / designer）不受影响。</p>
     */
    private boolean enableUi = false;

    /**
     * 内置管理后台的登录用户名。
     */
    private String username = "admin";

    /**
     * 内置管理后台的登录密码。
     */
    private String password = "flow@699";

    /**
     * 流程引擎核心配置组。
     */
    private Engine engine = new Engine();

    /**
     * 安全加密相关配置组。
     */
    private Security security = new Security();

    // ==================== Getters & Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnableUi() {
        return enableUi;
    }

    public void setEnableUi(boolean enableUi) {
        this.enableUi = enableUi;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    // ==================== 内部配置组：Engine ====================

    /**
     * 流程引擎核心配置。
     *
     * <p>对应 YAML 路径：{@code yu.flow.engine.*}</p>
     */
    public static class Engine {

        /**
         * 表达式引擎类型。
         * <ul>
         *   <li>{@code simple} —— 简化引擎（推荐），安全、快速、支持布尔表达式和路径访问</li>
         *   <li>{@code spel} —— Spring Expression Language，功能强大但有安全风险</li>
         * </ul>
         */
        private String expressionEngine = "simple";

        /**
         * 是否启用严格模式。
         * <p>开启后会在流程执行前验证所有节点配置的完整性。</p>
         */
        private boolean strictMode = true;

        /**
         * 是否启用执行追踪。
         * <p>开启后会记录每个节点的执行详情，便于调试但会影响性能。</p>
         */
        private boolean enableTrace = false;

        public String getExpressionEngine() {
            return expressionEngine;
        }

        public void setExpressionEngine(String expressionEngine) {
            this.expressionEngine = expressionEngine;
        }

        public boolean isStrictMode() {
            return strictMode;
        }

        public void setStrictMode(boolean strictMode) {
            this.strictMode = strictMode;
        }

        public boolean isEnableTrace() {
            return enableTrace;
        }

        public void setEnableTrace(boolean enableTrace) {
            this.enableTrace = enableTrace;
        }
    }

    // ==================== 内部配置组：Security ====================

    /**
     * 安全与加密相关配置。
     *
     * <p>对应 YAML 路径：{@code yu.flow.security.*}</p>
     */
    public static class Security {

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

        public String getAesSecretKey() {
            return aesSecretKey;
        }

        public void setAesSecretKey(String aesSecretKey) {
            this.aesSecretKey = aesSecretKey;
        }
    }
}
