package org.yu.flow.engine.evaluator.executor;

import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.step.ParallelStep;

import java.util.concurrent.ExecutorService;

/**
 * 并行网关：透传，由图上从 out 出发的多条边扇出。
 */
public class ParallelStepExecutor extends AbstractStepExecutor<ParallelStep> {

    public ParallelStepExecutor(FlowEngine engine, ExecutorService executorService) {
        // 保留构造签名以兼容 FlowEngine 注册调用；扇出由引擎 next 列表完成
    }

    @Override
    public String execute(ParallelStep step, ExecutionContext context, FlowDefinition flow) {
        return PortNames.OUT;
    }
}
