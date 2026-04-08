package org.yu.flow.engine.model.validation;

import lombok.Data;

/**
 * 参数校验规则
 * 用于定义 Start 节点输入参数的校验规则
 */
@Data
public class ValidationRule {

    /**
     * 是否必填
     */
    private boolean required = false;

    /**
     * 校验类型: phone, email, regex, range
     */
    private String type;

    /**
     * 正则表达式 (type=regex 时使用)
     */
    private String pattern;

    /**
     * 最小值 (type=range 时使用, 也可用于字符串长度)
     */
    private Number min;

    /**
     * 最大值 (type=range 时使用, 也可用于字符串长度)
     */
    private Number max;

    /**
     * 校验失败时的错误消息
     */
    private String message;
}
