package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.ExpressionEvaluator;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.SetVarStep;

/**
 * 变量设置执行器
 */
public class SetVarStepExecutor extends AbstractStepExecutor<SetVarStep> {
    private final ExpressionEvaluator evaluator;

    public SetVarStepExecutor(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String execute(SetVarStep step, ExecutionContext context, FlowDefinition flow) {
        evaluator.evaluateAssignment(step.getExpression(), context);
        return PortNames.OUT;
    }
}
