package org.yu.flow.engine.evaluator.expression;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.yu.flow.exception.FlowException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简化的表达式引擎实现
 * 只支持：布尔表达式 + 路径访问
 * 优势：安全、快速、够用
 */
public class SimpleExpressionEngine implements ExpressionEngine {

    // 变量占位符模式: ${varName}
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    // 布尔运算符
    private static final Pattern BOOLEAN_OPERATORS = Pattern.compile("(==|!=|>=|<=|>|<|&&|\\|\\||!)");

    @Override
    public boolean evaluateBoolean(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }

        // 替换变量
        String resolved = resolveVariables(expression, context);

        // 解析布尔表达式
        return parseBooleanExpression(resolved);
    }

    @Override
    public Object evaluatePath(String path, Map<String, Object> context) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        // 移除可能的 ${} 包裹
        String cleanPath = path.trim();
        if (cleanPath.startsWith("${") && cleanPath.endsWith("}")) {
            cleanPath = cleanPath.substring(2, cleanPath.length() - 1);
        }

        try {
            // 简单变量直接访问
            if (cleanPath.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                return context.get(cleanPath);
            }

            // 复杂路径使用 JsonPath
            // 将点号路径转换为 JsonPath: user.name -> $.user.name
            String jsonPath = cleanPath.startsWith("$") ? cleanPath : "$." + cleanPath;
            return JsonPath.read(context, jsonPath);

        } catch (PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw new FlowException("PATH_EVAL_ERROR", "路径求值失败: " + path, e);
        }
    }

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        // 判断是否为布尔表达式
        if (isBooleanExpression(expression)) {
            return evaluateBoolean(expression, context);
        }

        // 否则作为路径处理
        return evaluatePath(expression, context);
    }

    @Override
    public boolean isSafe(String expression) {
        if (expression == null) {
            return true;
        }

        // 检查危险关键字
        String[] dangerousKeywords = {
            "Runtime", "exec", "Class.forName",
            "System.", "ProcessBuilder",
            "T(", "#", "@" // SpEL 特殊语法
        };

        for (String keyword : dangerousKeywords) {
            if (expression.contains(keyword)) {
                return false;
            }
        }

        return true;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 替换表达式中的变量占位符
     */
    private String resolveVariables(String expression, Map<String, Object> context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = VAR_PATTERN.matcher(expression);

        while (matcher.find()) {
            String varPath = matcher.group(1);
            Object value = evaluatePath(varPath, context);

            // 转换为字符串表示
            String replacement = valueToString(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 将值转换为表达式字符串
     */
    private String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        // 对象类型返回 JSON 字符串
        return "\"" + value.toString() + "\"";
    }

    /**
     * 判断是否为布尔表达式
     */
    private boolean isBooleanExpression(String expression) {
        return BOOLEAN_OPERATORS.matcher(expression).find();
    }

    /**
     * 解析布尔表达式（简化实现）
     */
    private boolean parseBooleanExpression(String expression) {
        expression = expression.trim();

        // 处理逻辑或 ||
        if (expression.contains("||")) {
            String[] parts = expression.split("\\|\\|", 2);
            return parseBooleanExpression(parts[0]) || parseBooleanExpression(parts[1]);
        }

        // 处理逻辑与 &&
        if (expression.contains("&&")) {
            String[] parts = expression.split("&&", 2);
            return parseBooleanExpression(parts[0]) && parseBooleanExpression(parts[1]);
        }

        // 处理逻辑非 !
        if (expression.startsWith("!")) {
            return !parseBooleanExpression(expression.substring(1).trim());
        }

        // 处理比较运算符
        if (expression.contains("==")) {
            String[] parts = expression.split("==", 2);
            return compareEquals(parts[0].trim(), parts[1].trim());
        }

        if (expression.contains("!=")) {
            String[] parts = expression.split("!=", 2);
            return !compareEquals(parts[0].trim(), parts[1].trim());
        }

        if (expression.contains(">=")) {
            String[] parts = expression.split(">=", 2);
            return compareNumber(parts[0].trim(), parts[1].trim()) >= 0;
        }

        if (expression.contains("<=")) {
            String[] parts = expression.split("<=", 2);
            return compareNumber(parts[0].trim(), parts[1].trim()) <= 0;
        }

        if (expression.contains(">")) {
            String[] parts = expression.split(">", 2);
            return compareNumber(parts[0].trim(), parts[1].trim()) > 0;
        }

        if (expression.contains("<")) {
            String[] parts = expression.split("<", 2);
            return compareNumber(parts[0].trim(), parts[1].trim()) < 0;
        }

        // 直接布尔值
        if ("true".equalsIgnoreCase(expression)) {
            return true;
        }
        if ("false".equalsIgnoreCase(expression)) {
            return false;
        }

        throw new FlowException("INVALID_BOOLEAN_EXPR", "无效的布尔表达式: " + expression);
    }

    /**
     * 比较相等性
     */
    private boolean compareEquals(String left, String right) {
        Object leftVal = parseValue(left);
        Object rightVal = parseValue(right);

        if (leftVal == null && rightVal == null) {
            return true;
        }
        if (leftVal == null || rightVal == null) {
            return false;
        }

        return leftVal.equals(rightVal);
    }

    /**
     * 数值比较
     */
    private int compareNumber(String left, String right) {
        Double leftNum = parseNumber(left);
        Double rightNum = parseNumber(right);

        if (leftNum == null || rightNum == null) {
            throw new FlowException("COMPARE_ERROR", "无法比较非数值: " + left + ", " + right);
        }

        return leftNum.compareTo(rightNum);
    }

    /**
     * 解析值（字符串、数字、布尔）
     */
    private Object parseValue(String value) {
        value = value.trim();

        // null
        if ("null".equals(value)) {
            return null;
        }

        // 布尔
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }

        // 字符串（带引号）
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        // 数字
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            // 不是数字，返回原字符串
            return value;
        }
    }

    /**
     * 解析为数字
     */
    private Double parseNumber(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
