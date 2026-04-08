package org.yu.flow.engine.model;

/**
 * 流程引擎错误码枚举
 *
 * <p>所有 FlowException 的 errorCode 必须从此枚举取值，禁止随意拼写字符串。
 */
public enum ErrorCode {

    // ========== 解析阶段 ==========
    PARSE_ERROR("PARSE_ERROR", "流程 JSON 解析失败"),

    // ========== 节点查找 ==========
    NODE_NOT_FOUND("NODE_NOT_FOUND", "节点未找到"),

    // ========== 执行阶段 ==========
    NODE_EXECUTION_FAILED("NODE_EXECUTION_FAILED", "节点执行失败"),
    EXPRESSION_EVAL_ERROR("EXPRESSION_EVAL_ERROR", "表达式计算失败"),
    SYSTEM_VAR_EVAL_ERROR("SYSTEM_VAR_EVAL_ERROR", "系统变量求值失败"),

    // ========== 校验 ==========
    VALIDATION_FAILED("VALIDATION_FAILED", "参数校验失败"),

    // ========== 服务调用 ==========
    SERVICE_NOT_FOUND("SERVICE_NOT_FOUND", "服务未找到"),
    METHOD_NOT_FOUND("METHOD_NOT_FOUND", "方法未找到"),
    METHOD_INVOKE_ERROR("METHOD_INVOKE_ERROR", "方法调用失败"),

    // ========== IO ==========
    HTTP_REQUEST_FAILED("HTTP_REQUEST_FAILED", "HTTP 请求失败"),
    DATABASE_ERROR("DATABASE_ERROR", "数据库操作失败"),

    // ========== 安全 ==========
    SECURITY_VIOLATION("SECURITY_VIOLATION", "安全限制"),

    // ========== 并发 ==========
    COLLECT_TIMEOUT("COLLECT_TIMEOUT", "汇聚超时"),
    SCATTER_ERROR("SCATTER_ERROR", "分发错误");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** 获取错误码字符串 */
    public String code() {
        return code;
    }

    /** 获取默认错误描述 */
    public String defaultMessage() {
        return defaultMessage;
    }
}
