package org.yu.flow.engine.evaluator.expression;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.yu.flow.exception.FlowException;

import java.util.Map;

/**
 * Aviator 表达式求值器实现
 *
 * AviatorScript 语法特点:
 * - 变量直接引用，无需前缀: age >= 18
 * - 字符串使用单引号: 'Hello'
 * - 支持数学运算和逻辑运算
 *
 * @see <a href="https://github.com/killme2008/aviator">Aviator GitHub</a>
 */
public class AviatorEvaluatorImpl implements ExpressionEvaluatorStrategy {

    private static final AviatorEvaluatorInstance aviator = AviatorEvaluator.getInstance();

    @Override
    public Object evaluate(String expression, Map<String, Object> context) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        try {
            // Aviator 直接使用 context Map 作为环境变量
            return aviator.execute(expression, context);
        } catch (Exception e) {
            throw new FlowException("AVIATOR_EVAL_ERROR",
                    "Aviator 表达式求值失败: " + expression + ", 错误: " + e.getMessage(), e);
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
        // Aviator 可能返回数字类型作为布尔
        if (result instanceof Number) {
            return ((Number) result).doubleValue() != 0;
        }
        return Boolean.parseBoolean(result.toString());
    }
}
