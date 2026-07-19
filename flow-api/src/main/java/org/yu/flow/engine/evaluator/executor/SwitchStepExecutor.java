package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.expression.ExpressionEvaluatorFactory;
import org.yu.flow.engine.evaluator.expression.ExpressionEvaluatorStrategy;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.SwitchCase;
import org.yu.flow.engine.model.step.SwitchStep;
import org.yu.flow.exception.FlowException;

import java.util.List;
import java.util.Map;

/**
 * Switch 执行器：表达式求值一次，与分支 value 匹配后走 case_{id}。
 */
public class SwitchStepExecutor extends AbstractStepExecutor<SwitchStep> {

    @Override
    public String execute(SwitchStep step, ExecutionContext context, FlowDefinition flow) {
        try {
            Map<String, Object> inputs = this.prepareInputs(step, context, flow);
            ExpressionEvaluatorStrategy evaluator = ExpressionEvaluatorFactory.getEvaluator(step.getLanguage());
            Object result = evaluator.evaluate(step.getExpression(), inputs);
            String value = result != null ? result.toString() : "null";

            List<SwitchCase> cases = step.getCases();
            if (cases != null) {
                for (SwitchCase c : cases) {
                    if (c == null || c.getId() == null || c.getId().isBlank()) {
                        continue;
                    }
                    if (matches(c.getValue(), value, result)) {
                        return "case_" + c.getId();
                    }
                }
            }
            return PortNames.DEFAULT;

        } catch (FlowException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowException("SWITCH_EVAL_ERROR",
                    "SWITCH 表达式求值失败: " + step.getExpression() + ", 错误: " + e.getMessage(), e);
        }
    }

    private static boolean matches(String caseValue, String resultStr, Object result) {
        if (caseValue == null) {
            return false;
        }
        if (caseValue.equals(resultStr)) {
            return true;
        }
        if (result instanceof Number) {
            String numStr = String.valueOf(((Number) result).intValue());
            return caseValue.equals(numStr);
        }
        return false;
    }
}
