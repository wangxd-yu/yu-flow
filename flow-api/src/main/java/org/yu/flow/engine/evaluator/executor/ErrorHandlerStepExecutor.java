package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.ErrorHandlerStep;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误处理入口：将上下文中的 error 挂到本节点 out，再走下游补偿链路。
 */
public class ErrorHandlerStepExecutor extends AbstractStepExecutor<ErrorHandlerStep> {

    @Override
    public String execute(ErrorHandlerStep step, ExecutionContext context, FlowDefinition flow) {
        Object err = context.getVariable("error");
        Map<String, Object> out = new HashMap<>();
        out.put(PortNames.OUT, err);
        context.setVar(step.getId(), out);
        return PortNames.OUT;
    }
}
