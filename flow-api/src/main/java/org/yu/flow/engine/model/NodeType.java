package org.yu.flow.engine.model;

/**
 * 节点类型 ID 常量池
 *
 * <p>当前保持与测试用例兼容的旧命名 (camelCase)。
 * 未来前后端统一迁移至 kebab-case 时，只需修改此类中的值。
 */
public final class NodeType {

    private NodeType() { /* 防止实例化 */ }

    // ========== 基础出入口 ==========
    public static final String START = "start";
    public static final String END = "end";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";

    // ========== 基础逻辑 ==========
    public static final String EVALUATE = "evaluate";
    public static final String IF = "if";
    /** @deprecated 使用 {@link #IF} */
    public static final String CONDITION = "condition";
    public static final String SWITCH = "switch";

    // ========== 服务/IO ==========
    /** 服务调用（兼容测试中的 camelCase） */
    public static final String SERVICE_CALL = "serviceCall";
    /** 服务调用简写别名 */
    public static final String CALL = "call";
    /** HTTP 外部请求（兼容测试中的 camelCase） */
    public static final String HTTP_REQUEST = "httpRequest";
    /** HTTP 外部请求别名 */
    public static final String API = "api";
    public static final String DATABASE = "database";

    // ========== 循环与并发 ==========
    /** 串行循环（兼容测试中的 camelCase） */
    public static final String FOR_EACH = "forEach";
    /** Scatter 分发 */
    public static final String FOR = "for";
    /** Gather 屏障汇聚 */
    public static final String COLLECT = "collect";

    // ========== 数据与转换 ==========
    public static final String RECORD = "record";
    public static final String TEMPLATE = "template";
    /** 系统环境变量（兼容 camelCase） */
    public static final String SYSTEM_VAR = "systemVar";
    /** 系统方法调用（兼容 camelCase） */
    public static final String SYSTEM_METHOD = "systemMethod";

    // ========== 内部控制 ==========
    public static final String PARALLEL = "parallel";
    public static final String SET = "set";
    public static final String RETURN = "return";
}
