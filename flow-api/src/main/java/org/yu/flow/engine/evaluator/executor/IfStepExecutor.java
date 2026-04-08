package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.step.IfStep;

/**
 * IF 步骤执行器
 * 支持多表达式语言 (Aviator / SpEL / JS)
 */
public class IfStepExecutor extends AbstractExpressionStepExecutor<IfStep> {

    @Override
    protected String getExpression(IfStep step) {
        return step.getExpression();
    }

    @Override
    protected String getLanguage(IfStep step) {
        return step.getLanguage();
    }

    @Override
    protected String handleResult(Object evalResult, IfStep step, ExecutionContext context) {
        boolean result = toBoolean(evalResult);
        return result ? PortNames.TRUE : PortNames.FALSE;
    }
}
