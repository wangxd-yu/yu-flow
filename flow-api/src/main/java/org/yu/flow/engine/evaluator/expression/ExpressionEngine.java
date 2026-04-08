package org.yu.flow.engine.evaluator.expression;

import java.util.Map;

/**
 * 表达式引擎接口
 * 提供安全、高性能的表达式求值能力
 */
public interface ExpressionEngine {

    /**
     * 求值布尔表达式
     * @param expression 表达式字符串，如: "${age} >= 18", "${status} == 'active'"
     * @param context 变量上下文
     * @return 布尔结果
     */
    boolean evaluateBoolean(String expression, Map<String, Object> context);

    /**
     * 求值路径表达式
     * @param path 路径字符串，如: "user.name", "data[0].id"
     * @param context 变量上下文
     * @return 路径值
     */
    Object evaluatePath(String path, Map<String, Object> context);

    /**
     * 求值通用表达式（返回任意类型）
     * @param expression 表达式字符串
     * @param context 变量上下文
     * @return 求值结果
     */
    Object evaluate(String expression, Map<String, Object> context);

    /**
     * 检查表达式是否安全
     * @param expression 待检查的表达式
     * @return true 如果安全
     */
    boolean isSafe(String expression);
}
