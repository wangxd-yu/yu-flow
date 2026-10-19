package org.yu.flow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.yu.flow.config.properties.DbProperties;
import org.yu.flow.config.properties.DemoProperties;
import org.yu.flow.config.properties.EngineProperties;
import org.yu.flow.config.properties.IngressProperties;
import org.yu.flow.config.properties.MailProperties;
import org.yu.flow.config.properties.MetricsProperties;
import org.yu.flow.config.properties.MqProperties;
import org.yu.flow.config.properties.ObservabilityProperties;
import org.yu.flow.config.properties.OpenProperties;
import org.yu.flow.config.properties.OssProperties;
import org.yu.flow.config.properties.PrivacyProperties;
import org.yu.flow.config.properties.ReleaseProperties;
import org.yu.flow.config.properties.SecurityProperties;
import org.yu.flow.config.properties.TaskProperties;
import org.springframework.util.ClassUtils;

/**
 * yu-flow 引擎统一配置属性树。
 *
 * <p>所有配置项均收拢在 {@code yu.flow.*} 前缀下，各配置组拆在 {@code org.yu.flow.config.properties} 包里，
 * 确保在 IDE 中获得完整的自动补全与文档提示。</p>
 *
 * <h3>YAML 配置示例</h3>
 * <pre>
 * yu:
 *   flow:
 *     enabled: true
 *     username: admin
 *     password: 123456
 *     enable-ui: true
 *     engine:
 *       expression-engine: simple
 *       strict-mode: true
 *       enable-trace: false
 *     security:
 *       aes-secret-key: flow-secure-keys
 *     task:
 *       lock-ttl-minutes: 30
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
     * 是否开启演示模式。
     * <p>开启后将执行以下限制：</p>
     * <ul>
     *   <li>系统启动时已存在的所有 API、模型、数据源、目录资产将被锁定，不可修改或删除。</li>
     *   <li>Flow 引擎内的 Database 节点禁止执行 INSERT / UPDATE / DELETE 操作。</li>
     *   <li>通过 API 管理界面禁止新建 INSERT / UPDATE 类型的数据库操作接口。</li>
     * </ul>
     * <p>用户仍可自由创建新的查询类 API 并进行体验。</p>
     */
    private boolean demoMode = false;

    /**
     * 是否启用内置管理 UI 界面。
     * <p>关闭后管理后台将返回 403，但对外发布的页面（preview / designer）不受影响。</p>
     */
    private boolean enableUi = false;

    /**
     * 对外公开的 context 前缀（如网关嵌入时的 {@code /ssp}）。
     * <p>非空时优先于 servlet {@code context-path}，用于 Flow UI HTML 资源路径与前端 API 前缀。
     * 也可由请求头 {@code X-SSP-Public-Prefix} / {@code X-Forwarded-Prefix} 覆盖。</p>
     */
    private String publicContextPath;

    /**
     * 内置管理后台的登录用户名。
     */
    private String username = "admin";

    /**
     * 内置管理后台的登录密码。
     */
    private String password = "123456";

    /**
     * 流程引擎核心配置组。
     */
    @NestedConfigurationProperty
    private EngineProperties engine = new EngineProperties();

    /**
     * 安全加密相关配置组。
     */
    @NestedConfigurationProperty
    private SecurityProperties security = new SecurityProperties();

    /**
     * 接口出站隐私拦截（库内密文解密 / 脱敏 / 传输 SM4）。
     */
    @NestedConfigurationProperty
    private PrivacyProperties privacy = new PrivacyProperties();

    /**
     * 演示模式安全限制配置组。
     * <p>仅在 {@code demoMode=true} 时生效。</p>
     */
    @NestedConfigurationProperty
    private DemoProperties demo = new DemoProperties();

    /**
     * 定时任务相关配置组。
     */
    @NestedConfigurationProperty
    private TaskProperties task = new TaskProperties();

    /**
     * 资产运行计量配置组。
     */
    @NestedConfigurationProperty
    private MetricsProperties metrics = new MetricsProperties();

    /**
     * 第三方开放平台配置组。
     */
    @NestedConfigurationProperty
    private OpenProperties open = new OpenProperties();

    /**
     * 已发布 API 入站防护（全局默认；可按接口 securityConfig 覆盖）。
     */
    @NestedConfigurationProperty
    private IngressProperties ingress = new IngressProperties();

    /**
     * SMTP 邮件（告警 / 后续流程编排节点共用）。
     * 运行时优先读系统配置 MAIL_*；库中无键/停用时回退本段。
     */
    @NestedConfigurationProperty
    private MailProperties mail = new MailProperties();

    /**
     * 消息队列（MQ 触发 / 发送节点）配置组。
     */
    @NestedConfigurationProperty
    private MqProperties mq = new MqProperties();

    /**
     * 对象存储（MinIO / S3 兼容）配置组。
     */
    @NestedConfigurationProperty
    private OssProperties oss = new OssProperties();

    /**
     * 版本管理与跨环境发布配置组。
     */
    @NestedConfigurationProperty
    private ReleaseProperties release = new ReleaseProperties();

    /**
     * 可观测（Actuator 端点访问控制）配置组。
     */
    @NestedConfigurationProperty
    private ObservabilityProperties observability = new ObservabilityProperties();

    /**
     * 元数据库迁移。MySQL / PostgreSQL 默认启动时执行；瀚高不走 Flyway。
     */
    @NestedConfigurationProperty
    private DbProperties db = new DbProperties();

    // ==================== Getters & Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public boolean isEnableUi() {
        return enableUi;
    }

    public void setEnableUi(boolean enableUi) {
        this.enableUi = enableUi;
    }

    public String getPublicContextPath() {
        return publicContextPath;
    }

    public void setPublicContextPath(String publicContextPath) {
        this.publicContextPath = publicContextPath;
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

    public EngineProperties getEngine() {
        return engine;
    }

    public void setEngine(EngineProperties engine) {
        this.engine = engine;
    }

    public SecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(SecurityProperties security) {
        this.security = security;
    }

    public PrivacyProperties getPrivacy() {
        return privacy;
    }

    public void setPrivacy(PrivacyProperties privacy) {
        this.privacy = privacy;
    }

    public DemoProperties getDemo() {
        return demo;
    }

    public void setDemo(DemoProperties demo) {
        this.demo = demo;
    }

    public TaskProperties getTask() {
        return task;
    }

    public void setTask(TaskProperties task) {
        this.task = task;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    public OpenProperties getOpen() {
        return open;
    }

    public void setOpen(OpenProperties open) {
        this.open = open;
    }

    public IngressProperties getIngress() {
        return ingress;
    }

    public void setIngress(IngressProperties ingress) {
        this.ingress = ingress;
    }

    public MailProperties getMail() {
        return mail;
    }

    public void setMail(MailProperties mail) {
        this.mail = mail;
    }

    public MqProperties getMq() {
        return mq;
    }

    public void setMq(MqProperties mq) {
        this.mq = mq;
    }

    public OssProperties getOss() {
        return oss;
    }

    public void setOss(OssProperties oss) {
        this.oss = oss;
    }

    public ReleaseProperties getRelease() {
        return release;
    }

    public void setRelease(ReleaseProperties release) {
        this.release = release;
    }

    public ObservabilityProperties getObservability() {
        return observability;
    }

    public void setObservability(ObservabilityProperties observability) {
        this.observability = observability;
    }

    public DbProperties getDb() {
        return db;
    }

    public void setDb(DbProperties db) {
        this.db = db;
    }

    /**
     * OSS 模块是否实际装配，与 {@code @ConditionalOnOssEnabled} 一致：
     * {@code yu.flow.oss.enabled=true}（默认）且 classpath 存在 MinIO。
     * 供 /auth/me 告知前端是否展示 OSS 菜单。
     */
    public boolean isOssModuleActive() {
        return oss != null && oss.isEnabled()
                && ClassUtils.isPresent("io.minio.MinioClient", YuFlowProperties.class.getClassLoader());
    }
}
