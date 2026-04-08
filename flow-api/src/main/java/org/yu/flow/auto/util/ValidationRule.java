package org.yu.flow.auto.util;

import com.alibaba.fastjson.annotation.JSONField;

public class ValidationRule {
    private boolean required; // 是否必填
    private String message;   // 校验失败时的提示信息
    private String pattern;   // 正则表达式
    private Integer min;      // 最小值（适用于数字或字符串长度）
    private Integer max;      // 最大值（适用于数字或字符串长度）
    private String type;      // 数据类型（如 "string", "number", "date"）

    // 无参构造函数
    public ValidationRule() {}

    // 全参构造函数
    public ValidationRule(boolean required, String message, String pattern, Integer min, Integer max, String type) {
        this.required = required;
        this.message = message;
        this.pattern = pattern;
        this.min = min;
        this.max = max;
        this.type = type;
    }

    // Getters and Setters
    @JSONField(name = "required")
    public boolean isRequired() {
        return required;
    }

    @JSONField(name = "required")
    public void setRequired(boolean required) {
        this.required = required;
    }

    @JSONField(name = "message")
    public String getMessage() {
        return message;
    }

    @JSONField(name = "message")
    public void setMessage(String message) {
        this.message = message;
    }

    @JSONField(name = "pattern")
    public String getPattern() {
        return pattern;
    }

    @JSONField(name = "pattern")
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @JSONField(name = "min")
    public Integer getMin() {
        return min;
    }

    @JSONField(name = "min")
    public void setMin(Integer min) {
        this.min = min;
    }

    @JSONField(name = "max")
    public Integer getMax() {
        return max;
    }

    @JSONField(name = "max")
    public void setMax(Integer max) {
        this.max = max;
    }

    @JSONField(name = "type")
    public String getType() {
        return type;
    }

    @JSONField(name = "type")
    public void setType(String type) {
        this.type = type;
    }
}
