package org.yu.flow.engine.evaluator.expression;

import org.yu.flow.exception.FlowException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 安全的 SpEL 表达式求值器实现
 *
 * 安全特性:
 * - 使用 SimpleEvaluationContext 替代 StandardEvaluationContext
 * - 预解析检查：拒绝危险的 T() 类型引用和 new 构造器语法
 * - 禁用 Java 类型引用、Bean 引用、构造器调用
 * - 仅允许白名单内的安全函数
 *
 * 支持的安全函数 (通过 #Math 变量):
 * - #Math.max(a, b), #Math.min(a, b), #Math.abs(x)
 * - #Math.sqrt(x), #Math.pow(x, y), #Math.ceil(x), #Math.floor(x), #Math.round(x)
 * - #Math.sin(x), #Math.cos(x), #Math.tan(x)
 *
 * SpEL 语法 (安全模式):
 * - 变量使用 # 前缀: #age >= 18
 * - 调用安全函数: #Math.max(#a, #b)
 * - 支持字符串、数值比较和基本运算
 */
public class SpelEvaluatorImpl implements ExpressionEvaluatorStrategy {

    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final ConcurrentHashMap<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>(256);

    private static Expression getCachedExpression(String expressionString) {
        return EXPRESSION_CACHE.computeIfAbsent(expressionString, parser::parseExpression);
    }

    /**
     * 危险模式 - 用于安全检查
     */
    private static final Pattern TYPE_REFERENCE_PATTERN = Pattern.compile(
            "\\bT\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEW_KEYWORD_PATTERN = Pattern.compile(
            "\\bnew\\s+[a-zA-Z]", Pattern.CASE_INSENSITIVE);

    /**
     * 危险类名黑名单
     */
    private static final Set<String> DANGEROUS_CLASSES = new HashSet<>(Arrays.asList(
            "Runtime", "ProcessBuilder", "Class", "System", "ClassLoader",
            "Thread", "Method", "Field", "Constructor", "Unsafe",
            "ScriptEngine", "ScriptEngineManager"
    ));

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        // 1. 预解析安全检查 - 拒绝危险模式
        validateExpression(expression);

        try {
            // 2. 使用 SimpleEvaluationContext - 限制 SpEL 能力
            SimpleEvaluationContext.Builder builder = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withInstanceMethods();

            SimpleEvaluationContext evalContext = builder.build();

            // 3. 注册上下文变量 (支持 #varName 访问)
            if (context != null) {
                context.forEach(evalContext::setVariable);
            }

            // 4. 注册安全的 Math 辅助对象
            evalContext.setVariable("Math", new MathHelper());

            // 5. 解析并执行表达式
            Expression exp = getCachedExpression(expression);
            return exp.getValue(evalContext);

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage();
            // 提供友好的安全拦截提示
            if (message != null && (message.contains("EL1005E") || message.contains("EL1004E")
                    || message.contains("Type cannot be found"))) {
                throw new FlowException("SPEL_SECURITY_BLOCK",
                        "SpEL 安全限制: 不允许访问类型引用或危险操作. 表达式: " + expression, e);
            }
            throw new FlowException("SPEL_EVAL_ERROR",
                    "SpEL 表达式求值失败: " + expression + ", 错误: " + message, e);
        }
    }

    /**
     * 预解析表达式安全验证
     * 检测并拒绝包含危险模式的表达式
     */
    private void validateExpression(String expression) {
        // 检查 T() 类型引用
        if (TYPE_REFERENCE_PATTERN.matcher(expression).find()) {
            // 进一步检查是否引用了危险类
            for (String dangerous : DANGEROUS_CLASSES) {
                if (expression.contains(dangerous)) {
                    throw new FlowException("SPEL_SECURITY_BLOCK",
                            "SpEL 安全限制: 禁止访问危险类 '" + dangerous + "'. 表达式: " + expression);
                }
            }
            // 即使没有匹配到危险类名，也禁止 T() 语法 (因为可能是混淆绕过)
            throw new FlowException("SPEL_SECURITY_BLOCK",
                    "SpEL 安全限制: 禁止使用 T() 类型引用语法. 表达式: " + expression);
        }

        // 检查 new 构造器语法
        if (NEW_KEYWORD_PATTERN.matcher(expression).find()) {
            throw new FlowException("SPEL_SECURITY_BLOCK",
                    "SpEL 安全限制: 禁止使用 new 构造器语法. 表达式: " + expression);
        }
    }

    @Override
    public boolean evaluateBoolean(String expression, Map<String, Object> context) {
        Object result = evaluate(expression, context);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result == null) {
            return false;
        }
        return Boolean.parseBoolean(result.toString());
    }

    /**
     * 安全的 Math 辅助类，提供常用数学函数
     * 通过 #Math.xxx() 方式调用
     */
    public static class MathHelper {

        public Number max(Number a, Number b) {
            return Math.max(a.doubleValue(), b.doubleValue());
        }

        public Number min(Number a, Number b) {
            return Math.min(a.doubleValue(), b.doubleValue());
        }

        public Number abs(Number x) {
            return Math.abs(x.doubleValue());
        }

        public double sqrt(Number x) {
            return Math.sqrt(x.doubleValue());
        }

        public double pow(Number x, Number y) {
            return Math.pow(x.doubleValue(), y.doubleValue());
        }

        public double ceil(Number x) {
            return Math.ceil(x.doubleValue());
        }

        public double floor(Number x) {
            return Math.floor(x.doubleValue());
        }

        public long round(Number x) {
            return Math.round(x.doubleValue());
        }

        public double sin(Number x) {
            return Math.sin(x.doubleValue());
        }

        public double cos(Number x) {
            return Math.cos(x.doubleValue());
        }

        public double tan(Number x) {
            return Math.tan(x.doubleValue());
        }
    }
}
