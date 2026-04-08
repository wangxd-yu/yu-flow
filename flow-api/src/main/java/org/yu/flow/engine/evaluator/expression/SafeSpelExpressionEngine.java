package org.yu.flow.engine.evaluator.expression;

import org.yu.flow.exception.FlowException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 SpEL 的安全表达式引擎
 * 提供向后兼容性，但增加安全限制
 *
 * 注意：建议迁移到 SimpleExpressionEngine
 */
public class SafeSpelExpressionEngine implements ExpressionEngine {

    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final ConcurrentHashMap<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>(256);

    private static Expression getCachedExpression(String expressionString) {
        return EXPRESSION_CACHE.computeIfAbsent(expressionString, parser::parseExpression);
    }

    @Override
    public boolean evaluateBoolean(String expression, Map<String, Object> context) {
        Object result = evaluate(expression, context);

        if (result instanceof Boolean) {
            return (Boolean) result;
        }

        throw new FlowException("TYPE_ERROR", "表达式结果不是布尔类型: " + expression);
    }

    @Override
    public Object evaluatePath(String path, Map<String, Object> context) {
        return evaluate(path, context);
    }

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        // 安全检查
        if (!isSafe(expression)) {
            throw new FlowException("UNSAFE_EXPRESSION", "检测到不安全的表达式: " + expression);
        }

        try {
            // 使用 SimpleEvaluationContext 替代 StandardEvaluationContext
            // 禁用类型引用、构造器调用、Bean 引用等危险功能
            EvaluationContext evalContext = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .withRootObject(context)
                .build();

            Expression exp = getCachedExpression(expression);
            return exp.getValue(evalContext);

        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR", "表达式求值失败: " + expression, e);
        }
    }

    @Override
    public boolean isSafe(String expression) {
        if (expression == null) {
            return true;
        }

        // 危险模式列表
        String[] dangerousPatterns = {
            "T\\(",              // 类型引用: T(java.lang.Runtime)
            "#",                 // SpEL 变量引用
            "@",                 // Bean 引用
            "new ",              // 构造器调用
            "Runtime",           // 运行时类
            "ProcessBuilder",    // 进程构建器
            "Class\\.forName",   // 反射
            "System\\.",         // 系统类
            "\\.getClass\\(",    // 获取类
            "exec\\(",           // 执行方法
        };

        for (String pattern : dangerousPatterns) {
            if (expression.matches(".*" + pattern + ".*")) {
                return false;
            }
        }

        return true;
    }
}
