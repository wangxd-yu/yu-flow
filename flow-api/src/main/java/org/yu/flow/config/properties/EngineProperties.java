package org.yu.flow.config.properties;

/**
 * 流程引擎核心配置。
 *
 * <p>对应 YAML 路径：{@code yu.flow.engine.*}</p>
 */
public class EngineProperties {

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
     * 单次 JavaScript 求值允许执行的语句数上限，超过即终止（防死循环）；≤0 不限制。
     */
    private long scriptStatementLimit = 2_000_000L;

    /**
     * 单次 JavaScript 求值的墙钟超时（毫秒），到时强制取消；≤0 不限制。
     */
    private long scriptTimeoutMs = 10_000L;

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

    /**
     * 全局默认日志策略模式（资产级 logMode=SYSTEM_DEFAULT 时继承此值）。
     * <ul>
     *   <li>{@code ERROR_ONLY} — 默认推荐：仅报错时记录，成本低、排障有保障</li>
     *   <li>{@code ALL}        — 全量记录所有请求（高存储成本，适合联调阶段）</li>
     *   <li>{@code OFF}        — 完全关闭，不记录任何日志</li>
     * </ul>
     */
    private String defaultLogMode = "ERROR_ONLY";

    public String getDefaultLogMode() {
        return defaultLogMode;
    }

    public void setDefaultLogMode(String defaultLogMode) {
        this.defaultLogMode = defaultLogMode;
    }

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

    public long getScriptStatementLimit() {
        return scriptStatementLimit;
    }

    public void setScriptStatementLimit(long scriptStatementLimit) {
        this.scriptStatementLimit = scriptStatementLimit;
    }

    public long getScriptTimeoutMs() {
        return scriptTimeoutMs;
    }

    public void setScriptTimeoutMs(long scriptTimeoutMs) {
        this.scriptTimeoutMs = scriptTimeoutMs;
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
