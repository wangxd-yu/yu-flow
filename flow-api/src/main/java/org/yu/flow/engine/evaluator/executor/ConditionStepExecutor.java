package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.ExpressionEvaluator;
import org.yu.flow.engine.model.ConditionCase;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.step.ConditionStep;


/**
 * 条件步骤执行器
 */
public class ConditionStepExecutor extends AbstractStepExecutor<ConditionStep> {
    private final ExpressionEvaluator evaluator;

    public ConditionStepExecutor(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String execute(ConditionStep step, ExecutionContext context, FlowDefinition flow) {
        // 1. 遍历所有条件分支
        for (ConditionCase cc : step.getCases()) {
            if (matchCondition(cc, context)) {
                executeAction(cc, context);
                return "then";
            }
        }

        // 2. 默认分支（不需要Action直接返回else即可，UI连线至对应逻辑节点即可）
        return "else";
    }

    private boolean matchCondition(ConditionCase cc, ExecutionContext ctx) {
        if (cc.getExpression() == null) {
            return false;
        }

        try {
            Boolean result = (Boolean) evaluator.evaluate(cc.getExpression(), ctx);
            return result != null && result;
        } catch (Exception e) {
            throw new FlowException("CONDITION_EVAL_ERROR",
                    "条件表达式求值失败: " + cc.getExpression(), e);
        }
    }

    private void executeAction(ConditionCase cc, ExecutionContext ctx) {
        if (cc.getThrowError() != null) {
            throw new FlowException("500", cc.getThrowError());
        }
        if (cc.getSetVar() != null) {
            evaluator.evaluateAssignment(cc.getSetVar(), ctx);
        }
    }
}
