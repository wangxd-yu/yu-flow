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
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    /** 定时调度入口节点（任务管理专用） */
    public static final String SCHEDULE = "schedule";
    /** 内部服务编排入口节点（服务编排专用） */
    public static final String SERVICE = "service";
    /** MQ 消息触发入口节点（MQ 任务专用） */
    public static final String MQ_TRIGGER = "mqTrigger";

    // ========== 基础逻辑 ==========
    public static final String EVALUATE = "evaluate";
    public static final String IF = "if";
    public static final String SWITCH = "switch";

    // ========== 服务/IO ==========
    /** HTTP 外部请求 */
    public static final String HTTP_REQUEST = "httpRequest";
    /** 内部编排调用（Flow API 或内部服务，由 targetType 区分） */
    public static final String API = "api";
    public static final String DATABASE = "database";

    // ========== 循环与并发 ==========
    /** 串行循环：按顺序处理列表（item → done） */
    public static final String FOR_EACH = "forEach";
    /** Scatter 分发（并发扇出，需配 Collect） */
    public static final String FOR = "for";
    /** Gather 屏障汇聚 */
    public static final String COLLECT = "collect";
    /**
     * 并行网关：图上从 out 拉多条边即并行扇出。
     */
    public static final String PARALLEL = "parallel";

    // ========== 数据与转换 ==========
    public static final String RECORD = "record";
    public static final String TEMPLATE = "template";
    /** 系统环境变量 */
    public static final String SYSTEM_VAR = "systemVar";
    /** 系统方法调用 */
    public static final String SYSTEM_METHOD = "systemMethod";

    // ========== 内部控制 ==========
    /** 延迟 / 等待（毫秒） */
    public static final String DELAY = "delay";
    /** 发送邮件（SMTP，复用 FlowMailService） */
    public static final String SEND_MAIL = "sendMail";
    /** 发送 MQ 消息（RabbitMQ / Kafka，走 MqProvider SPI） */
    public static final String MQ_SEND = "mqSend";
    /** 统一错误处理入口（引擎异常时跳转，单例） */
    public static final String ERROR_HANDLER = "errorHandler";
}
