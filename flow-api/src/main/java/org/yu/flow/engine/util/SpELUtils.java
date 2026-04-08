package org.yu.flow.engine.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.expression.*;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SpELUtils {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final Map<String, Expression> EXPR_CACHE = new ConcurrentHashMap<>(1024);

    private static Expression getCachedExpression(String expressionString) {
        return EXPR_CACHE.computeIfAbsent(expressionString, parser::parseExpression);
    }

    /**
     * 通用 SpEL 处理动态数据
     *
     * @param input          输入数据（Map/POJO/JSON String/List）
     * @param spelExpression SpEL 表达式
     * @param returnType     返回类型（如 Map.class, List.class, String.class）
     */
    public static <T> T process(Object input, String spelExpression, Class<T> returnType) {
        try {
            JsonNode rootNode = mapper.valueToTree(input);
            StandardEvaluationContext context = new StandardEvaluationContext(rootNode);
            Object result = getCachedExpression(spelExpression).getValue(context);
            return mapper.convertValue(result, returnType);
        } catch (Exception e) {
            throw new RuntimeException("SpEL 处理失败: " + e.getMessage(), e);
        }
    }

    // 重载方法（返回 Map 或 List）
    public static Map<String, Object> processToMap(Object input, String spel) {
        return process(input, spel, Map.class);
    }

    public static List<Object> processToList(Object input, String spel) {
        return process(input, spel, List.class);
    }

    public static boolean isPotentialSpELExpression(String input) {
        if (input == null) {
            return false;
        }
        return (input.startsWith("#{") && input.endsWith("}"))
                || (input.startsWith("${") && input.endsWith("}"))
                || input.contains("T(")
                || input.contains("new ")
                || input.matches(".*[+\\-*/%^].*")
                || input.matches(".*\\?.*|.*:.*");
    }

    public static Object safeParseExpression(String input) {
        return safeParseExpression(input, null);
    }

    public static Object safeParseExpression(String input, EvaluationContext evalContext) {
        if (!isPotentialSpELExpression(input)) {
            return input; // 不是 SpEL，直接返回原字符串
        }

        try {
            Expression exp = getCachedExpression(input);
            return (evalContext != null)
                    ? exp.getValue(evalContext)  // 使用传入的 EvaluationContext
                    : exp.getValue();            // 默认使用空上下文
        } catch (SpelEvaluationException | ParseException e) {
            return input; // 解析失败，返回原字符串
        }
    }

    public static void main(String[] args) {
        String test1 = "你好";          // 普通字符串
        String test2 = "#{1 + 1}";     // SpEL 表达式
        String test3 = "${user.name}"; // 属性占位符风格（需自定义 ParserContext）

        // [DEBUG] removed println // 输出: 你好
        // [DEBUG] removed println // 输出: 2
        // [DEBUG] removed println // 输出: ${user.name}（未提供上下文，无法解析）
    }
}
