package org.yu.flow.exception;

import java.util.Collections;
import java.util.List;

/**
 * JSON Schema 入参校验异常
 *
 * <p>当动态 API 的请求体不满足前端定义的 JSON Schema 规则时抛出。
 * 区别于 {@link ValidationException}（流程定义校验），本异常专用于运行时入参校验，
 * 携带结构化的校验错误列表，供全局异常处理器返回 400 状态码。</p>
 *
 * @author yu-flow
 */
public class SchemaValidationException extends RuntimeException {

    /** 所有校验错误的中文友好提示列表 */
    private final List<String> errors;

    public SchemaValidationException(String message) {
        super(message);
        this.errors = Collections.singletonList(message);
    }

    public SchemaValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors != null ? errors : Collections.singletonList(message);
    }

    public List<String> getErrors() {
        return errors;
    }
}
