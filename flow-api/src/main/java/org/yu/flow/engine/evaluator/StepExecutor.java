package org.yu.flow.engine.evaluator;

import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.Step;

/**
 * 步骤执行器接口
 */
public interface StepExecutor<T extends Step> {
    /**
     * 执行步骤
     * @param step 步骤定义
     * @param context 执行上下文
     * @param flow 完整流程定义
     * @return 触发的输出端口名称
     */
    String execute(T step, ExecutionContext context, FlowDefinition flow);
}
