package org.yu.flow.engine.evaluator.expression;

import java.util.Map;

/**
 * 表达式求值策略接口
 * 定义表达式引擎的统一契约
 */
public interface ExpressionEvaluatorStrategy {

    /**
     * 求值表达式，返回任意类型结果
     *
     * @param expression 表达式字符串
     * @param context 变量上下文 (变量名 -> 值)
     * @return 求值结果
     */
    Object evaluate(String expression, Map<String, Object> context);

    /**
     * 求值布尔表达式
     *
     * @param expression 布尔表达式字符串
     * @param context 变量上下文
     * @return 布尔结果
     */
    default boolean evaluateBoolean(String expression, Map<String, Object> context) {
        Object result = evaluate(expression, context);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result == null) {
            return false;
        }
        // 尝试字符串转换
        return Boolean.parseBoolean(result.toString());
    }
}
