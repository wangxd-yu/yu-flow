package org.yu.flow.engine.model;

/**
 * 执行上下文内置 Key 常量池
 *
 * <p>所有写入 ExecutionContext 的保留字段、前缀必须通过此类引用。
 */
public final class ContextKeys {

    private ContextKeys() { /* 防止实例化 */ }

    // ========== 内部缓存前缀 ==========
    /** Scatter-Gather 屏障计数器前缀 */
    public static final String BARRIER_PREFIX = "__barrier_";
    /** SystemVar 懒加载缓存前缀 */
    public static final String SYS_VAR_CACHE_PREFIX = "CACHE_SYS_VAR_";

    // ========== 标准节点输出 Key ==========
    /** 节点计算结果 */
    public static final String RESULT = "result";
    /** Start 节点传入参数 */
    public static final String ARGS = "args";
    /** Collect 节点汇聚计数 */
    public static final String COUNT = "count";

    // ========== ForEach 迭代专用 ==========
    /** 当前循环索引 */
    public static final String INDEX = "index";
}
