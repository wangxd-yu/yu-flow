package org.yu.flow.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用响应包装类
 *
 * @param <T> 数据类型
 */
@Data
public class R<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean ok;     // 操作是否成功（true/false）
    private int code;       // 状态码
    private String msg;     // 消息
    private T data;         // 数据
    private long timestamp = System.currentTimeMillis(); // 时间戳

    public R() {
    }

    public R(int code, String msg, T data) {
        this.ok = ResultCode.SUCCESS.getCode() == code; // 根据code自动设置ok
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ==================== 成功静态方法 ====================
    public static <T> R<T> ok() {
        return ok(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMsg(),
                data
        );
    }

    public static <T> R<T> ok(T data, String msg) {
        R<T> result = new R<>();
        result.setOk(true);
        result.setCode(200);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    // ==================== 失败静态方法 ====================
    public static <T> R<T> fail() {
        return fail(ResultCode.FAILED);
    }

    public static <T> R<T> fail(String msg) {
        return fail(ResultCode.FAILED.getCode(), msg);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> result = new R<>(code, msg, null);
        result.setOk(false); // 显式设置ok为false
        return result;
    }

    public static <T> R<T> fail(IResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMsg());
    }

    // ==================== 链式调用支持 ====================
    public static <T> R<T> ok(int code, String msg, T data) {
        R<T> result = new R<>();
        result.setOk(code == ResultCode.SUCCESS.getCode());
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }

    public static <T> R<T> fail(int code, String msg, T data) {
        R<T> result = new R<>();
        result.setOk(false);
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
