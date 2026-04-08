package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.step.CollectStep;
import org.yu.flow.exception.FlowException;

import java.util.*;

/**
 * CollectStepExecutor - 并发汇聚屏障执行器（Gather 端）
 *
 * 作为 Scatter-Gather 机制的"最后一道关卡"，汇总并发分支的数据并接力执行下游。
 */
@Slf4j
public class CollectStepExecutor extends AbstractStepExecutor<CollectStep> {

    private final FlowEngine engine;

    public CollectStepExecutor(FlowEngine engine) {
        this.engine = engine;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(CollectStep step, ExecutionContext context, FlowDefinition flow) {
        String barrierKey = ForStepExecutor.BARRIER_KEY_PREFIX + step.getId();
        Object barrierObj = context.getVariable(barrierKey);

        if (!(barrierObj instanceof ForStepExecutor.LoopBarrier)) {
            // 如果不存在 barrier，说明流程没有经由 ForStep 正常触发并发，这在纯 Scatter-Gather 模式下是非法的
            throw new FlowException("MISSING_BARRIER", "CollectStep 必须与 ForStep 配合使用，未找到关联的 LoopBarrier");
        }

        return executeBarrierMode(step, context, flow, (ForStepExecutor.LoopBarrier) barrierObj);
    }

    // ========================================================================
    // 模式 A：并发屏障（核心逻辑）
    // ========================================================================

    /**
     * 并发屏障模式执行逻辑
     *
     * <p><b>这是整个 Scatter-Gather 机制中最关键的方法</b>，
     * "线程接力"的精华就在 if/else 的两条分支中：</p>
     *
     * <pre>
     *   // 第一步：提取当前分支的业务结果并入队（无锁，线程安全）
     *   Object itemValue = extractItemValue(step, context, flow);
     *   barrier.results.offer(itemValue);
     *
     *   // 第二步：原子计数 +1，利用返回值唯一性判断"是否最后"
     *   int arrivedCount = barrier.counter.incrementAndGet();
     *
     *   // 第三步：关键判断
     *   if (arrivedCount &lt; barrier.totalCount) {
     *     // ← 不是最后，当前线程的使命完成，静默退出
     *     return null;
     *   }
     *
     *   // ← 只有最后一条线程到达这里（arrivedCount == totalCount）
     *   // 执行"线程接力"：成为新的主线程，完成聚合并继续执行下游
     *   handoffToDownstream(barrier, step, flow);
     *   return null; // 自身 runBranchFlow 也干净退出
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private String executeBarrierMode(CollectStep step, ExecutionContext context,
                                       FlowDefinition flow,
                                       ForStepExecutor.LoopBarrier barrier) {
        String stepId = step.getId();

        // ====================================================================
        // 第一步：提取当前分支的业务结果
        //
        // 通过 CollectStep 的 inputs 配置指定从当前分支上下文中提取哪个字段。
        // 典型配置：{ "val": { "extractPath": "$.calc.result" } }
        // 若未配置，视 inputs 的第一个键对应的值；若 inputs 为空，透传整个上下文快照。
        // ====================================================================
        Object itemValue = extractItemValue(step, context, flow);
        log.debug("CollectStep [{}]: 当前分支提交数据: {}", stepId, itemValue);

        // ====================================================================
        // 第二步：无锁入队 + 原子计数
        // ConcurrentLinkedQueue.offer() 是无锁线程安全的
        // ====================================================================
        barrier.results.offer(itemValue != null ? itemValue : "__NULL__");

        // ====================================================================
        // 第三步：【核心】原子 +1，判断"是否最后一条线程"
        //
        // 高并发安全性说明：
        // AtomicInteger.incrementAndGet() 的每次调用返回唯一的整数值（1, 2, 3...）。
        // 恰好等于 totalCount 的返回值只会出现一次，因此只有唯一一条线程会进入
        // "线程接力"分支，其余线程均进入 "return null" 分支。
        // 注意：这里不使用 compareAndSet，因为每条线程的返回值本来就不同。
        // ====================================================================
        int arrivedCount = barrier.counter.incrementAndGet();

        log.info("CollectStep [{}]: 分支到达 {}/{}", stepId, arrivedCount, barrier.totalCount);

        if (arrivedCount < barrier.totalCount) {
            // ================================================================
            // 【非最后线程】：任务完成，当前线程静默退出
            //
            // return null 的含义：
            //   FlowEngine.runFlow() 循环中: currentStep.getNext().get(null) = null
            //   → nextTarget == null → currentStep = null → while 退出
            //   → 当前分支线程的 runBranchFlow 正常结束，该线程生命终止。
            // ================================================================
            log.debug("CollectStep [{}]: 未凑齐（{}/{}），线程静默退出", stepId, arrivedCount, barrier.totalCount);
            return null; // ← 当前线程就此消失
        }

        // ====================================================================
        // 【最后一条线程到达！执行"线程接力"（Thread Handoff）】
        //
        // 从这里开始，当前线程升格为"主执行线程"，承担以下职责：
        //   1. 将 results 队列转为有序 List（此时无并发竞争）
        //   2. 将聚合结果写入"主上下文"（barrier.mainContext，非当前的 branchContext 副本）
        //   3. 以主上下文为载体，调用 engine.runBranchFlow() 执行 CollectStep 下游节点
        //   4. 下游执行完毕后，调用 completionFuture.complete() 唤醒阻塞中的主线程
        //   5. return null，自身的 runBranchFlow 干净退出
        // ====================================================================
        log.info("CollectStep [{}]: 【线程接力】最后一条线程到达 ({}/{})，开始聚合并接管主流程",
                stepId, arrivedCount, barrier.totalCount);

        performHandoff(step, context, flow, barrier);

        // 自身的 runBranchFlow 也干净地退出（主流程已经在上面的 performHandoff 中执行完毕）
        return null;
    }

    /**
     * 执行线程接力：最后一条线程组装结果，写入主上下文，继续执行下游节点，最后唤醒主线程
     */
    @SuppressWarnings("unchecked")
    private void performHandoff(CollectStep step, ExecutionContext branchContext,
                                 FlowDefinition flow, ForStepExecutor.LoopBarrier barrier) {
        String stepId = step.getId();

        // ---- 1. 组装聚合 List（此时无并发竞争，安全转换）----
        List<Object> finalList = new ArrayList<>();
        boolean hasError = barrier.hasError;
        for (Object item : barrier.results) {
            if ("__NULL__".equals(item)) {
                finalList.add(null);
            } else {
                if (item instanceof Map) {
                    Object flag = ((Map<?, ?>) item).get("__type__");
                    if ("SCATTER_GATHER_ERROR".equals(flag)) {
                        hasError = true; // 记录有错误分支，但仍加入列表（业务层可过滤）
                    }
                }
                finalList.add(item);
            }
        }

        // ---- 2. 将聚合结果写入主上下文（注意！是 barrier.mainContext，不是 branchContext）----
        //       之所以写主上下文：engine.execute() 在 completionFuture 等待结束后，
        //       会从主上下文的 output 读取最终结果，branchContext 不会被主线程读取。
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(PortNames.LIST, finalList);
        resultMap.put(ContextKeys.COUNT, finalList.size());
        resultMap.put("hasError", hasError);
        barrier.mainContext.setVar(stepId, resultMap);

        log.info("CollectStep [{}]: 聚合完成，count={}, hasError={}，写入主上下文", stepId, finalList.size(), hasError);

        // ---- 3. 确定 CollectStep 的下游节点（list 优先，其次 finish）----
        String downstreamStepId = null;
        Object listTarget = step.getNext().get("list");
        Object finishTarget = step.getNext().get("finish");

        if (listTarget != null) {
            downstreamStepId = resolveFirstStepId(listTarget);
        } else if (finishTarget != null) {
            downstreamStepId = resolveFirstStepId(finishTarget);
        }

        // ---- 4. 【线程接力核心】以主上下文为载体，执行 CollectStep 下游节点 ----
        //       这里的关键：使用 barrier.mainContext（主上下文），不是 branchContext
        //       这样下游节点（包括 EndStep）的执行结果最终落在主上下文中，
        //       engine.execute() 的主线程唤醒后可以直接读取。
        if (downstreamStepId != null) {
            try {
                log.info("CollectStep [{}]: 线程接力 → 执行下游节点 [{}] (使用主上下文)", stepId, downstreamStepId);
                engine.runBranchFlow(downstreamStepId, barrier.mainContext, flow);
                log.info("CollectStep [{}]: 下游执行完毕", stepId);
            } catch (Exception e) {
                log.error("CollectStep [{}]: 下游执行异常: {}", stepId, e.getMessage(), e);
            }
        } else {
            log.warn("CollectStep [{}]: list/finish 端口均未连接下游节点", stepId);
        }

        // ---- 5. 唤醒阻塞在 engine.execute() 的主线程 ----
        //       在下游执行完毕后再 complete，确保主线程唤醒时 mainContext.output 已就绪。
        if (!barrier.completionFuture.isDone()) {
            barrier.completionFuture.complete(null);
            log.info("CollectStep [{}]: completionFuture.complete() → 主线程唤醒", stepId);
        }
    }

    /**
     * 从当前分支上下文中提取要收集的业务数据
     *
     * <p>提取优先级：</p>
     * <ol>
     *   <li>CollectStep 的 inputs 配置（如 {@code "val": {"extractPath": "$.calc.result"}}）</li>
     *   <li>Fallback：当前分支上下文的 output</li>
     * </ol>
     */
    private Object extractItemValue(CollectStep step, ExecutionContext context, FlowDefinition flow) {
        // 1. 优先使用 V3.1 inputs 配置
        Map<String, Object> inputs = this.prepareInputs(step, context, flow);
        if (!inputs.isEmpty()) {
            return inputs.values().iterator().next();
        }

        // 2. Fallback：返回分支上下文的 output
        return context.getOutput();
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /**
     * 从 next 目标中解析第一个步骤 ID
     */
    private String resolveFirstStepId(Object nextTarget) {
        if (nextTarget instanceof String) return (String) nextTarget;
        if (nextTarget instanceof List && !((List<?>) nextTarget).isEmpty()) {
            return ((List<?>) nextTarget).get(0).toString();
        }
        return null;
    }
}
