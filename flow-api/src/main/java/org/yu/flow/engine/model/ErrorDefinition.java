package org.yu.flow.engine.model;

import lombok.Data;

/**
 * 错误定义模型
 */
@Data
public class ErrorDefinition {
    private int code;       // HTTP状态码或错误码
    private String message; // 错误信息

    public static ErrorDefinition of(int code, String message) {
        ErrorDefinition def = new ErrorDefinition();
        def.setCode(code);
        def.setMessage(message);
        return def;
    }
}
