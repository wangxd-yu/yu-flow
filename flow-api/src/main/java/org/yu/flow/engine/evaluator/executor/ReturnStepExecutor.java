package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.ExpressionEvaluator;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.ReturnStep;

/**
 * 返回步骤执行器
 */
public class ReturnStepExecutor extends AbstractStepExecutor<ReturnStep> {
    private final ExpressionEvaluator evaluator;

    public ReturnStepExecutor(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public String execute(ReturnStep step, ExecutionContext context, FlowDefinition flow) {
        Object value = evaluator.evaluate(step.getValue(), context);
        context.setOutput(value);
        return null; // 返回节点没有后续
    }
}
