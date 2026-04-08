package org.yu.flow.engine.evaluator.validator;

import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.validation.ValidationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 参数校验器
 * 支持必填、手机号、邮箱、正则、范围等校验
 */
public class ParamValidator {

    /**
     * 手机号正则 - 中国大陆手机号格式
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 邮箱正则
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    /**
     * 校验输入参数
     *
     * @param validations 校验规则配置
     * @param args 输入参数
     * @throws FlowException 校验失败时抛出异常
     */
    public static void validate(Map<String, ValidationRule> validations, Map<String, Object> args) {
        if (validations == null || validations.isEmpty()) {
            return;
        }

        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, ValidationRule> entry : validations.entrySet()) {
            String fieldName = entry.getKey();
            ValidationRule rule = entry.getValue();
            Object value = args != null ? args.get(fieldName) : null;

            String error = validateField(fieldName, value, rule);
            if (error != null) {
                errors.add(error);
            }
        }

        if (!errors.isEmpty()) {
            throw new FlowException("VALIDATION_ERROR", String.join("; ", errors));
        }
    }

    /**
     * 校验单个字段
     */
    private static String validateField(String fieldName, Object value, ValidationRule rule) {
        // 1. 必填校验
        if (rule.isRequired()) {
            if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
                return rule.getMessage() != null ? rule.getMessage() : fieldName + " 不能为空";
            }
        }

        // 如果值为空且不是必填，跳过后续校验
        if (value == null || (value instanceof String && ((String) value).trim().isEmpty())) {
            return null;
        }

        String type = rule.getType();
        if (type == null) {
            return null;
        }

        String stringValue = String.valueOf(value);

        switch (type.toLowerCase()) {
            case "phone":
                if (!PHONE_PATTERN.matcher(stringValue).matches()) {
                    return rule.getMessage() != null ? rule.getMessage() : fieldName + " 手机号格式不正确";
                }
                break;

            case "email":
                if (!EMAIL_PATTERN.matcher(stringValue).matches()) {
                    return rule.getMessage() != null ? rule.getMessage() : fieldName + " 邮箱格式不正确";
                }
                break;

            case "regex":
                String pattern = rule.getPattern();
                if (pattern != null && !Pattern.matches(pattern, stringValue)) {
                    return rule.getMessage() != null ? rule.getMessage() : fieldName + " 格式不正确";
                }
                break;

            case "range":
                try {
                    double numValue = Double.parseDouble(stringValue);
                    Number min = rule.getMin();
                    Number max = rule.getMax();

                    if (min != null && numValue < min.doubleValue()) {
                        return rule.getMessage() != null ? rule.getMessage() :
                                fieldName + " 不能小于 " + min;
                    }
                    if (max != null && numValue > max.doubleValue()) {
                        return rule.getMessage() != null ? rule.getMessage() :
                                fieldName + " 不能大于 " + max;
                    }
                } catch (NumberFormatException e) {
                    return fieldName + " 必须是数字";
                }
                break;

            case "length":
                int len = stringValue.length();
                Number minLen = rule.getMin();
                Number maxLen = rule.getMax();

                if (minLen != null && len < minLen.intValue()) {
                    return rule.getMessage() != null ? rule.getMessage() :
                            fieldName + " 长度不能少于 " + minLen + " 个字符";
                }
                if (maxLen != null && len > maxLen.intValue()) {
                    return rule.getMessage() != null ? rule.getMessage() :
                            fieldName + " 长度不能超过 " + maxLen + " 个字符";
                }
                break;
        }

        return null;
    }
}
