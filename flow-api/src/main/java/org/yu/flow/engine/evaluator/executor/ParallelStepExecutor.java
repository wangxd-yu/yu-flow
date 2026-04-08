package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.exception.FlowException;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.step.ParallelStep;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * 并行步骤执行器
 */
@Slf4j
public class ParallelStepExecutor extends AbstractStepExecutor<ParallelStep> {
    private final FlowEngine engine;
    private final ExecutorService executorService;

    public ParallelStepExecutor(FlowEngine engine, ExecutorService executorService) {
        this.engine = engine;
        this.executorService = executorService;
    }

    @Override
    public String execute(ParallelStep step, ExecutionContext context, FlowDefinition flow) {
        List<java.util.concurrent.CompletableFuture<ExecutionContext>> futures = new ArrayList<>();

        for (Step task : step.getTasks()) {
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                // 采用浅拷贝
                ExecutionContext taskContext = context.copy(false);
                engine.executeStepDirectly(task, taskContext, flow);
                return taskContext;
            }, executorService));
        }

        try {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            for (java.util.concurrent.CompletableFuture<ExecutionContext> future : futures) {
                ExecutionContext taskContext = future.get();
                taskContext.getVar().forEach((k, v) -> {
                    // 注意：这只是把顶层新产生的变量更新回到主上下文，不覆盖原有的！
                    if (!context.hasVariable(k)) {
                        context.setVar(k, v);
                    }
                });
            }
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (step.getErrorMode() == ParallelStep.ErrorMode.FAST_FAIL) {
                throw new FlowException("PARALLEL_TASK_FAILED", "并行任务失败", cause);
            }
            log.warn("并行任务执行失败(已忽略)", cause);
        } catch (Exception e) {
            if (step.getErrorMode() == ParallelStep.ErrorMode.FAST_FAIL) {
                throw new FlowException("PARALLEL_TASK_FAILED", "并行任务执行期产生未知错误", e);
            }
        }
        return PortNames.OUT;
    }
}
