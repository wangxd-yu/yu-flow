package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.expression.ExpressionEvaluatorFactory;
import org.yu.flow.engine.evaluator.expression.ExpressionEvaluatorStrategy;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.SwitchStep;

import java.util.Map;

/**
 * SWITCH 步骤执行器
 * 支持多表达式语言 (Aviator / SpEL)
 */
public class SwitchStepExecutor extends AbstractStepExecutor<SwitchStep> {

    @Override
    public String execute(SwitchStep step, ExecutionContext context, FlowDefinition flow) {
        try {
            // 1. 准备输入变量 (复用父类的 prepareInputs 方法)
            Map<String, Object> inputs = this.prepareInputs(step, context, flow);

            // 2. 获取对应语言的表达式求值器
            ExpressionEvaluatorStrategy evaluator = ExpressionEvaluatorFactory.getEvaluator(step.getLanguage());

            // 3. 求值表达式获取结果值
            Object result = evaluator.evaluate(step.getExpression(), inputs);
            String value = result != null ? result.toString() : "null";

            // 4. 构建输出端口名称 (case_VALUE 格式)
            // 如果有 cases 列表，检查是否匹配；否则直接使用 case_value 格式
            if (step.getCases() != null && !step.getCases().isEmpty()) {
                // 有明确的 cases 列表
                if (step.getCases().contains(value)) {
                    return "case_" + value;
                }
                // 数值类型匹配 (处理 200 vs "200" 的情况)
                if (result instanceof Number) {
                    String numStr = String.valueOf(((Number) result).intValue());
                    if (step.getCases().contains(numStr)) {
                        return "case_" + numStr;
                    }
                }
                return "default";
            } else {
                // 没有 cases 列表，直接使用 case_value 格式 (动态端口)
                // 如果 next 中有对应的 case_value 端口，则使用; 否则返回 default
                String casePort = "case_" + value;
                if (step.getNext().containsKey(casePort)) {
                    return casePort;
                }
                return "default";
            }

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("SWITCH_EVAL_ERROR",
                    "SWITCH 表达式求值失败: " + step.getExpression() + ", 错误: " + e.getMessage(), e);
        }
    }
}
