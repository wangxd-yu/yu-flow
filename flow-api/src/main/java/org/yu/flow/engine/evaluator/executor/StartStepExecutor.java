package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.StartStep;

import org.yu.flow.engine.evaluator.validator.ParamValidator;
import java.util.HashMap;
import java.util.Map;

/**
 * Start 起点节点执行器
 * 初始化上下文，存储输入参数
 */
public class StartStepExecutor extends AbstractStepExecutor<StartStep> {

    @Override
    public String execute(StartStep step, ExecutionContext context, FlowDefinition flow) {
        // Start 节点将其 args 存储到上下文中
        // 输入参数已经在 FlowEngine.execute() 中处理过

        // 1. 参数校验
        ParamValidator.validate(step.getValidations(), context.getVar());

        // 2. 将 start 节点的数据结构存入上下文
        Map<String, Object> startData = new HashMap<>();
        startData.put(ContextKeys.ARGS, context.getVar()); // 引用当前上下文变量作为 args
        context.setVar("start", startData);

        return PortNames.OUT;
    }
}
