package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.expression.ExpressionEvaluatorFactory;
import org.yu.flow.engine.evaluator.expression.ExpressionEvaluatorStrategy;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.exception.FlowException;

import java.util.Collection;
import java.util.Map;

/**
 * 抽象的表达式步骤执行器
 * 使用模板方法模式，统一处理 inputs 参数注入和脚本引擎 (GraalJS/Aviator/SpEL) 执行
 *
 * @param <T> 具体步骤类型
 */
public abstract class AbstractExpressionStepExecutor<T extends Step> extends AbstractStepExecutor<T> {

    @Override
    public String execute(T step, ExecutionContext context, FlowDefinition flow) {
        try {
            // 1. 准备输入变量 (复用父类的 prepareInputs 方法)
            Map<String, Object> inputs = this.prepareInputs(step, context, flow);

            // 2. 获取对应语言的表达式求值器
            ExpressionEvaluatorStrategy evaluator = ExpressionEvaluatorFactory.getEvaluator(getLanguage(step));

            // 3. 求值表达式
            Object evalResult = evaluator.evaluate(getExpression(step), inputs);

            // 4. 交由子类处理求值结果并返回下一步端口
            return handleResult(evalResult, step, context);

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("EXPRESSION_EVAL_ERROR",
                    "表达式求值失败: " + getExpression(step) + ", 错误: " + e.getMessage(), e);
        }
    }

    /**
     * 获取当前步骤定义的表达式
     *
     * @param step 步骤定义
     * @return 表达式字符串
     */
    protected abstract String getExpression(T step);

    /**
     * 获取当前步骤定义的表达式语言 (如 aviator, spel, js)
     *
     * @param step 步骤定义
     * @return 语言标识
     */
    protected abstract String getLanguage(T step);

    /**
     * 处理表达式执行结果，并决定返回值如何影响流程走向
     *
     * @param evalResult 表达式求值结果
     * @param step       当前步骤
     * @param context    执行上下文
     * @return 下一个连接端口名称
     */
    protected abstract String handleResult(Object evalResult, T step, ExecutionContext context);

    /**
     * 严格的真值判断工具方法
     * 支持 Boolean, 0/非0 Number, "true"/"false" String, 集合判空, null 判断
     *
     * @param result 求值结果
     * @return boolean 真值
     */
    protected boolean toBoolean(Object result) {
        if (result == null) {
            return false;
        }
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        if (result instanceof Number) {
            // 0 视为 false，其他视为 true
            return ((Number) result).doubleValue() != 0;
        }
        if (result instanceof String) {
            String str = ((String) result).trim().toLowerCase();
            if ("true".equals(str)) {
                return true;
            }
            if ("false".equals(str)) {
                return false;
            }
            // 针对非 true/false 的字符串，非空即为 true
            return !str.isEmpty();
        }
        if (result instanceof Collection) {
            return !((Collection<?>) result).isEmpty();
        }
        if (result instanceof Map) {
            return !((Map<?, ?>) result).isEmpty();
        }
        if (result.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(result) > 0;
        }
        return true; // 默认其他非空对象为 true
    }
}
