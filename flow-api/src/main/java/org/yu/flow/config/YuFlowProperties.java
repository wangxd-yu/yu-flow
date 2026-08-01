package org.yu.flow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
    private Engine engine = new Engine();

    /**
     * 安全加密相关配置组。
     */
    private Security security = new Security();

    /**
     * 演示模式安全限制配置组。
     * <p>仅在 {@code demoMode=true} 时生效。</p>
     */
    private Demo demo = new Demo();

    /**
     * 定时任务相关配置组。
     */
    private Task task = new Task();

    /**
     * 资产运行计量配置组。
     */
    private Metrics metrics = new Metrics();

    /**
     * 第三方开放平台配置组。
     */
    private Open open = new Open();

    /**
     * 已发布 API 入站防护（全局默认；可按接口 securityConfig 覆盖）。
     */
    private Ingress ingress = new Ingress();

    /**
     * SMTP 邮件（告警 / 后续流程编排节点共用）。
     * 运行时优先读系统配置 MAIL_*；库中无键/停用时回退本段。
     */
    private Mail mail = new Mail();

    /**
     * 消息队列（MQ 触发 / 发送节点）配置组。
     */
    private Mq mq = new Mq();

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

    public Demo getDemo() {
        return demo;
    }

    public void setDemo(Demo demo) {
        this.demo = demo;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public Open getOpen() {
        return open;
    }

    public void setOpen(Open open) {
        this.open = open;
    }

    public Ingress getIngress() {
        return ingress;
    }

    public void setIngress(Ingress ingress) {
        this.ingress = ingress;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Mq getMq() {
        return mq;
    }

    public void setMq(Mq mq) {
        this.mq = mq;
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

        /**
         * Groovy 已编译脚本缓存上限。超过上限时关闭并淘汰最早的类加载器。
         */
        private int groovyScriptCacheSize = 256;

        /**
         * DSL → FlowDefinition 编译缓存上限（按内容 SHA-256 去重）。
         */
        private int definitionCacheMaxSize = 256;

        /**
         * DSL 编译缓存：多久未访问后过期（分钟）。
         */
        private long definitionCacheExpireMinutes = 60;

        /** Trace 变量快照最大嵌套深度 */
        private int traceSnapshotMaxDepth = 6;

        /** Trace 快照中集合最大元素数 */
        private int traceSnapshotMaxCollectionSize = 32;

        /** Trace 快照中单个字符串最大长度 */
        private int traceSnapshotMaxStringLength = 1024;

        /** Trace 快照中 Map 最大条目数 */
        private int traceSnapshotMaxMapEntries = 64;

        /**
         * 落库时是否仅保留失败步的 inputs/outputs（成功步只留状态与耗时）。
         */
        private boolean tracePersistFailedStepsOnly = false;

        /**
         * traceData JSON 最大字节数；超出则递进收缩。
         */
        private int traceDataMaxBytes = 512 * 1024;

        /**
         * 引擎并行池 core 大小。≤0 表示 {@code CPU * 2}。
         */
        private int poolCoreSize = 0;

        /**
         * 引擎并行池 max 大小。≤0 表示 {@code CPU * 4}。
         */
        private int poolMaxSize = 0;

        /** 引擎并行池有界队列容量 */
        private int poolQueueCapacity = 1024;

        /**
         * For 节点同时在途分支上限（Semaphore）。≤0 不限制（仅受线程池约束）。
         */
        private int forMaxInFlight = 64;

        /**
         * CALL 用 ResolvedContent 缓存上限（按 publishedSnapshot 内容 hash）。
         */
        private int resolvedContentCacheMaxSize = 256;

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

        public int getGroovyScriptCacheSize() {
            return groovyScriptCacheSize;
        }

        public void setGroovyScriptCacheSize(int groovyScriptCacheSize) {
            this.groovyScriptCacheSize = groovyScriptCacheSize;
        }

        public int getDefinitionCacheMaxSize() {
            return definitionCacheMaxSize;
        }

        public void setDefinitionCacheMaxSize(int definitionCacheMaxSize) {
            this.definitionCacheMaxSize = definitionCacheMaxSize;
        }

        public long getDefinitionCacheExpireMinutes() {
            return definitionCacheExpireMinutes;
        }

        public void setDefinitionCacheExpireMinutes(long definitionCacheExpireMinutes) {
            this.definitionCacheExpireMinutes = definitionCacheExpireMinutes;
        }

        public int getTraceSnapshotMaxDepth() {
            return traceSnapshotMaxDepth;
        }

        public void setTraceSnapshotMaxDepth(int traceSnapshotMaxDepth) {
            this.traceSnapshotMaxDepth = traceSnapshotMaxDepth;
        }

        public int getTraceSnapshotMaxCollectionSize() {
            return traceSnapshotMaxCollectionSize;
        }

        public void setTraceSnapshotMaxCollectionSize(int traceSnapshotMaxCollectionSize) {
            this.traceSnapshotMaxCollectionSize = traceSnapshotMaxCollectionSize;
        }

        public int getTraceSnapshotMaxStringLength() {
            return traceSnapshotMaxStringLength;
        }

        public void setTraceSnapshotMaxStringLength(int traceSnapshotMaxStringLength) {
            this.traceSnapshotMaxStringLength = traceSnapshotMaxStringLength;
        }

        public int getTraceSnapshotMaxMapEntries() {
            return traceSnapshotMaxMapEntries;
        }

        public void setTraceSnapshotMaxMapEntries(int traceSnapshotMaxMapEntries) {
            this.traceSnapshotMaxMapEntries = traceSnapshotMaxMapEntries;
        }

        public boolean isTracePersistFailedStepsOnly() {
            return tracePersistFailedStepsOnly;
        }

        public void setTracePersistFailedStepsOnly(boolean tracePersistFailedStepsOnly) {
            this.tracePersistFailedStepsOnly = tracePersistFailedStepsOnly;
        }

        public int getTraceDataMaxBytes() {
            return traceDataMaxBytes;
        }

        public void setTraceDataMaxBytes(int traceDataMaxBytes) {
            this.traceDataMaxBytes = traceDataMaxBytes;
        }

        public int getPoolCoreSize() {
            return poolCoreSize;
        }

        public void setPoolCoreSize(int poolCoreSize) {
            this.poolCoreSize = poolCoreSize;
        }

        public int getPoolMaxSize() {
            return poolMaxSize;
        }

        public void setPoolMaxSize(int poolMaxSize) {
            this.poolMaxSize = poolMaxSize;
        }

        public int getPoolQueueCapacity() {
            return poolQueueCapacity;
        }

        public void setPoolQueueCapacity(int poolQueueCapacity) {
            this.poolQueueCapacity = poolQueueCapacity;
        }

        public int getForMaxInFlight() {
            return forMaxInFlight;
        }

        public void setForMaxInFlight(int forMaxInFlight) {
            this.forMaxInFlight = forMaxInFlight;
        }

        public int getResolvedContentCacheMaxSize() {
            return resolvedContentCacheMaxSize;
        }

        public void setResolvedContentCacheMaxSize(int resolvedContentCacheMaxSize) {
            this.resolvedContentCacheMaxSize = resolvedContentCacheMaxSize;
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
    }

    // ==================== 内部配置组：Demo ====================

    /**
     * 演示模式安全限制配置。
     *
     * <p>对应 YAML 路径：{@code yu.flow.demo.*}</p>
     * <pre>
     * yu:
     *   flow:
     *     demo-mode: true
     *     demo:
     *       max-steps: 200
     *       max-for-loop-items: 50
     * </pre>
     */
    public static class Demo {

        /**
         * 单次流程执行允许的最大步骤数。
         * <p>用于防止死循环和过于复杂的流程编排。
         * 执行步骤数超过此值时，引擎将强制终止流程并报错。</p>
         */
        private int maxSteps = 200;

        /**
         * For / ForEach 循环节点允许的最大输入数组元素数。
         * <p>防止传入巨大数组导致线程池耗尽或内存溢出。</p>
         */
        private int maxForLoopItems = 50;

        /**
         * Delay 节点允许的最大等待毫秒数（演示模式）。
         */
        private long maxDelayMs = 5000L;

        public int getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
        }

        public int getMaxForLoopItems() {
            return maxForLoopItems;
        }

        public void setMaxForLoopItems(int maxForLoopItems) {
            this.maxForLoopItems = maxForLoopItems;
        }

        public long getMaxDelayMs() {
            return maxDelayMs;
        }

        public void setMaxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }
    }

    // ==================== 内部配置组：Task ====================

    /**
     * 定时任务相关配置。
     *
     * <p>对应 YAML 路径：{@code yu.flow.task.*}</p>
     * <pre>
     * yu:
     *   flow:
     *     task:
     *       lock-ttl-minutes: 30
     *       lock-renew-interval-seconds: 60
     *       scheduler-pool-size: 4
     * </pre>
     */
    public static class Task {

        /**
         * 分布式执行锁 TTL（分钟）。
         * <p>Cron 触发时通过 Redis 抢锁，防止多节点重复执行；超时后锁自动释放。</p>
         */
        private int lockTtlMinutes = 30;

        /**
         * 锁心跳续期间隔（秒）。≤0 关闭续期。
         * <p>建议为 TTL 的 1/2～1/3，长任务执行期间可防止 TTL 提前丢失导致双跑。</p>
         */
        private int lockRenewIntervalSeconds = 60;

        /** Cron / 手动触发共用的 TaskScheduler 池大小 */
        private int schedulerPoolSize = 4;

        /** flowAsyncExecutor 核心线程数 */
        private int asyncCorePoolSize = 4;

        /** flowAsyncExecutor 最大线程数 */
        private int asyncMaxPoolSize = 8;

        /** flowAsyncExecutor 队列容量 */
        private int asyncQueueCapacity = 200;

        public int getLockTtlMinutes() {
            return lockTtlMinutes;
        }

        public void setLockTtlMinutes(int lockTtlMinutes) {
            this.lockTtlMinutes = lockTtlMinutes;
        }

        public int getLockRenewIntervalSeconds() {
            return lockRenewIntervalSeconds;
        }

        public void setLockRenewIntervalSeconds(int lockRenewIntervalSeconds) {
            this.lockRenewIntervalSeconds = lockRenewIntervalSeconds;
        }

        public int getSchedulerPoolSize() {
            return schedulerPoolSize;
        }

        public void setSchedulerPoolSize(int schedulerPoolSize) {
            this.schedulerPoolSize = schedulerPoolSize;
        }

        public int getAsyncCorePoolSize() {
            return asyncCorePoolSize;
        }

        public void setAsyncCorePoolSize(int asyncCorePoolSize) {
            this.asyncCorePoolSize = asyncCorePoolSize;
        }

        public int getAsyncMaxPoolSize() {
            return asyncMaxPoolSize;
        }

        public void setAsyncMaxPoolSize(int asyncMaxPoolSize) {
            this.asyncMaxPoolSize = asyncMaxPoolSize;
        }

        public int getAsyncQueueCapacity() {
            return asyncQueueCapacity;
        }

        public void setAsyncQueueCapacity(int asyncQueueCapacity) {
            this.asyncQueueCapacity = asyncQueueCapacity;
        }
    }

    // ==================== 内部配置组：Metrics ====================

    /**
     * 资产运行计量配置。
     *
     * <p>对应 YAML 路径：{@code yu.flow.metrics.*}</p>
     */
    public static class Metrics {

        /** 总开关；关闭后 Recorder 直接 no-op */
        private boolean enabled = true;

        /** 是否将服务 DEBUG 触发计入生产计量 */
        private boolean includeDebug = false;

        /** Redis 分钟桶 TTL（小时） */
        private int redisTtlHours = 3;

        /** MySQL 分钟汇总保留天数；≤0 表示不清理 */
        private int retainDays = 90;

        /** Flush 间隔（秒） */
        private int flushIntervalSeconds = 30;

        /** Flush 分布式锁 TTL（秒） */
        private int flushLockTtlSeconds = 55;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeDebug() {
            return includeDebug;
        }

        public void setIncludeDebug(boolean includeDebug) {
            this.includeDebug = includeDebug;
        }

        public int getRedisTtlHours() {
            return redisTtlHours;
        }

        public void setRedisTtlHours(int redisTtlHours) {
            this.redisTtlHours = redisTtlHours;
        }

        public int getRetainDays() {
            return retainDays;
        }

        public void setRetainDays(int retainDays) {
            this.retainDays = retainDays;
        }

        public int getFlushIntervalSeconds() {
            return flushIntervalSeconds;
        }

        public void setFlushIntervalSeconds(int flushIntervalSeconds) {
            this.flushIntervalSeconds = flushIntervalSeconds;
        }

        public int getFlushLockTtlSeconds() {
            return flushLockTtlSeconds;
        }

        public void setFlushLockTtlSeconds(int flushLockTtlSeconds) {
            this.flushLockTtlSeconds = flushLockTtlSeconds;
        }
    }

    // ==================== 内部配置组：Open ====================

    /**
     * 第三方开放平台配置。
     *
     * <p>对应 YAML：{@code yu.flow.open.*}</p>
     */
    public static class Open {

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
         * 已发布 API 且无 AppKey 时，是否强制 {@link org.yu.flow.module.open.auth.HostAuthenticationProbe}。
         * 默认 false（宽松）；宿主误配 permitAll 时可打开并实现 Probe。
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

    // ==================== 内部配置组：Ingress ====================

    /**
     * 已发布 API 入站防护全局默认。
     *
     * <p>对应 YAML：{@code yu.flow.ingress.*}</p>
     * <p>{@code enabled=false} 时信任宿主网关（与历史行为一致）；开放入口 {@code /flow-api/open/**} 不受影响。</p>
     */
    public static class Ingress {

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

    // ==================== 内部配置组：Mail ====================

    /**
     * SMTP 邮件发送。对应 YAML：{@code yu.flow.mail.*}
     * <p>系统配置 MAIL_* 优先于本段。</p>
     */
    public static class Mail {
        private boolean enabled = false;
        private String host = "";
        private int port = 465;
        private String username = "";
        private String password = "";
        /** 发件人地址；空则用 username */
        private String from = "";
        private boolean ssl = true;
        private boolean starttls = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
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

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        public boolean isStarttls() {
            return starttls;
        }

        public void setStarttls(boolean starttls) {
            this.starttls = starttls;
        }
    }

    // ==================== 内部配置组：Mq ====================

    /**
     * 消息队列（MQ 触发 / 发送节点）配置。
     *
     * <p>对应 YAML 路径：{@code yu.flow.mq.*}</p>
     * <pre>
     * yu:
     *   flow:
     *     mq:
     *       consumer-enabled: true
     *       dedup-ttl-seconds: 300
     *       send-timeout-ms: 10000
     *       max-message-bytes: 1048576
     * </pre>
     */
    public static class Mq {

        /**
         * 是否启动 MQ 消费（MqConsumerManager 总开关）。
         * <p>多实例部署时仅一个实例开启即可避免重复消费竞争；
         * 消息级幂等由 Redis 锁按 messageId 兜底。</p>
         */
        private boolean consumerEnabled = true;

        /** 消费幂等锁 TTL（秒），按 messageId 去重 */
        private int dedupTtlSeconds = 300;

        /** 发送超时（毫秒） */
        private long sendTimeoutMs = 10000;

        /** 消息体大小上限（字节），≤0 不限制。默认 1MB */
        private int maxMessageBytes = 1048576;

        public boolean isConsumerEnabled() {
            return consumerEnabled;
        }

        public void setConsumerEnabled(boolean consumerEnabled) {
            this.consumerEnabled = consumerEnabled;
        }

        public int getDedupTtlSeconds() {
            return dedupTtlSeconds;
        }

        public void setDedupTtlSeconds(int dedupTtlSeconds) {
            this.dedupTtlSeconds = dedupTtlSeconds;
        }

        public long getSendTimeoutMs() {
            return sendTimeoutMs;
        }

        public void setSendTimeoutMs(long sendTimeoutMs) {
            this.sendTimeoutMs = sendTimeoutMs;
        }

        public int getMaxMessageBytes() {
            return maxMessageBytes;
        }

        public void setMaxMessageBytes(int maxMessageBytes) {
            this.maxMessageBytes = maxMessageBytes;
        }
    }
}
