package org.yu.flow.engine.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.expression.*;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.yu.flow.engine.evaluator.spel.MacroSpelContexts;

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
     * 通用 SpEL 处理动态数据（SafeTypeLocator + Bean 白名单）。
     */
    public static <T> T process(Object input, String spelExpression, Class<T> returnType) {
        try {
            JsonNode rootNode = mapper.valueToTree(input);
            StandardEvaluationContext context = MacroSpelContexts.create(rootNode);
            Object result = getCachedExpression(spelExpression).getValue(context);
            return mapper.convertValue(result, returnType);
        } catch (Exception e) {
            throw new RuntimeException("SpEL 处理失败: " + e.getMessage(), e);
        }
    }

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
            return input;
        }

        try {
            Expression exp = getCachedExpression(input);
            EvaluationContext ctx = evalContext != null ? evalContext : MacroSpelContexts.create();
            return exp.getValue(ctx);
        } catch (SpelEvaluationException | ParseException e) {
            return input;
        }
    }
}
