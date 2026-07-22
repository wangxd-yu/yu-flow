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
         * Groovy 脚本可通过 spring.getBean(name) 获取的 Bean 名称白名单。
         * 默认空列表，不向脚本暴露任何 Spring Bean。
         */
        private List<String> scriptAllowedBeans = new ArrayList<>();

        public String getAesSecretKey() {
            return aesSecretKey;
        }

        public void setAesSecretKey(String aesSecretKey) {
            this.aesSecretKey = aesSecretKey;
        }

        public List<String> getScriptAllowedBeans() {
            return scriptAllowedBeans;
        }

        public void setScriptAllowedBeans(List<String> scriptAllowedBeans) {
            this.scriptAllowedBeans = scriptAllowedBeans == null ? new ArrayList<>() : scriptAllowedBeans;
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
}
