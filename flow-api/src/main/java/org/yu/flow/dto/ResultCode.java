package org.yu.flow.dto;

/**
 * 结果状态码枚举
 */
public enum ResultCode implements IResultCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(400, "参数校验失败"),

    // 登录相关状态码
    LOGIN_ERROR(401001, "用户名或密码错误"),
    TOKEN_EMPTY(401002, "token不能为空"),
    TOKEN_INVALID(401003, "token不合法"),
    TOKEN_EXPIRED(401004, "token已过期"),

    // 权限相关状态码
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),

    // 资源相关状态码
    NOT_FOUND(404, "资源不存在"),

    // 业务相关状态码
    BUSINESS_ERROR(500001, "业务逻辑错误"),
    DATABASE_ERROR(500002, "数据库操作异常"),
    SYSTEM_ERROR(500003, "系统异常");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
