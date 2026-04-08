package org.yu.flow.engine.evaluator.executor;
import org.yu.flow.engine.model.PortNames;
import org.yu.flow.engine.model.ContextKeys;

import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.evaluator.ExecutionContext;
import org.yu.flow.engine.evaluator.FlowEngine;
import org.yu.flow.engine.model.FlowDefinition;
import org.yu.flow.engine.model.Step;
import org.yu.flow.engine.model.step.ForStep;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ForStepExecutor - 并发分散执行器（Scatter 端）
 *
 * <h2>核心设计理念：发射即忘 (Fire-and-Forget)</h2>
 *
 * <p>ForStepExecutor 是整个 Scatter-Gather 机制中的"发射台"。它的职责极其单纯：</p>
 * <ol>
 *   <li>解析输入数组（处理空数组特殊情况）</li>
 *   <li>初始化全局并发屏障 {@link LoopBarrier}，注册到主上下文</li>
 *   <li>为每个数组元素向线程池 submit 一个异步分支任务</li>
 *   <li><b>立刻 return null</b> —— 主调用线程的 runFlow while 循环随即终止</li>
 * </ol>
 *
 * <h2>为什么 return null？</h2>
 * <p>FlowEngine 的 runFlow 循环结构如下：</p>
 * <pre>
 *   while (currentStep != null) {
 *     String nextPort = executeStep(currentStep, ...);   // ← 这里是 null
 *     Object nextTarget = currentStep.getNext().get(null); // → null
 *     if (nextTarget == null) {
 *       currentStep = null;  // while 循环终止
 *     }
 *   }
 * </pre>
 * <p>ForStep 返回 null 后，主干调度循环自然退出。而并发分支任务已经在后台跑起来了。
 * engine.execute() 随后会检测主上下文中的 {@link LoopBarrier}，并在其
 * {@link LoopBarrier#completionFuture} 上阻塞等待，直到最后一条分支线程
 * 完成所有下游节点的执行。</p>
 *
 * <h2>空数组旁路（防死锁）</h2>
 * <p>当输入为 null 或 [] 时：</p>
 * <ol>
 *   <li>创建 totalCount=0 的 LoopBarrier（completionFuture 立即完成）</li>
 *   <li>直接触发 CollectStep 的下游（通过 engine.runBranchFlow），输出空 List</li>
 *   <li>return null，主干不再等待任何分支</li>
 * </ol>
 *
 * <h2>分支上下文与屏障共享</h2>
 * <p>ExecutionContext.copy() 在深拷贝时，对非 Map 类型的对象直接传引用（而非克隆）。
 * LoopBarrier 不是 Map，因此所有分支上下文中的屏障引用指向<b>同一个对象</b>，
 * 这是并发安全的底层基石。</p>
 */
@Slf4j
public class ForStepExecutor extends AbstractStepExecutor<ForStep> {

    /**
     * LoopBarrier 在 ExecutionContext 中的 key 前缀。
     * 完整 key: {@code ContextKeys.BARRIER_PREFIX + collectStepId}
     */
    public static final String BARRIER_KEY_PREFIX = ContextKeys.BARRIER_PREFIX;

    private final FlowEngine engine;
    private final ExecutorService executorService;

    public ForStepExecutor(FlowEngine engine, ExecutorService executorService) {
        this.engine = engine;
        this.executorService = executorService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(ForStep step, ExecutionContext context, FlowDefinition flow) {

        // ====================================================================
        // Step 1: 解析输入数组
        // ====================================================================
        List<?> items = resolveList(step, context, flow);
        String collectStepId = step.getCollectStepId();
        String barrierKey = BARRIER_KEY_PREFIX + (collectStepId != null ? collectStepId : step.getId());

        log.info("ForStep [{}] 启动: 数组长度={}, collectStepId={}", step.getId(),
                items == null ? "null" : items.size(), collectStepId);

        // ====================================================================
        // Step 2: 创建全局并发屏障，注册到主上下文
        //
        // 关键：LoopBarrier 不是 Map 类型，ExecutionContext 深拷贝时会原样传引用，
        // 因此所有分支上下文共享同一个 LoopBarrier 实例。
        // ====================================================================
        int totalCount = (items == null || items.isEmpty()) ? 0 : items.size();
        LoopBarrier barrier = new LoopBarrier(
                step.getId(), collectStepId, totalCount, context, step.getTimeoutMs());
        context.setVar(barrierKey, barrier);

        // ====================================================================
        // Step 3: 空数组特殊路径（防止 CollectStep 永久挂起）
        // ====================================================================
        if (totalCount == 0) {
            log.warn("ForStep [{}]: 空数组，直接触发 CollectStep [{}] 完成信号", step.getId(), collectStepId);
            triggerEmptyCollect(step, collectStepId, barrier, context, flow);
            // 主干 return null → runFlow 循环退出
            // engine.execute() 会检测 barrier.completionFuture（已完成），立即读取结果
            return null;
        }

        // ====================================================================
        // Step 4: 获取 item 端口的下游首节点 id（分支从此出发）
        // ====================================================================
        Object itemTarget = step.getNext().get("item");
        String itemStartStepId = resolveFirstStepId(itemTarget);
        if (itemStartStepId == null) {
            log.error("ForStep [{}]: item 端口未连接任何下游节点，无法发射分支！", step.getId());
            barrier.forceComplete(Collections.emptyList());
            return null;
        }

        // ====================================================================
        // Step 5: 并发 Scatter —— Fire-and-Forget
        //
        // 每个分支任务：
        //   a) 深拷贝主上下文（barrier 因非 Map 传引用，所有分支共享）
        //   b) 注入当前元素的循环元数据
        //   c) 调用 engine.runBranchFlow()（不 stopAtJoinNodes），全程执行直到
        //      CollectStep（在 CollectStepExecutor 中决定是 return null 还是继续）
        //   d) 若此分支为"最后一条"（CollectStepExecutor 已接管主流程），
        //      则分支任务 lambda 结束时将 branchContext 的输出合并回 mainContext
        // ====================================================================
        log.info("ForStep [{}]: 发射 {} 条并发分支，item 端口首节点=[{}]",
                step.getId(), totalCount, itemStartStepId);

        for (int i = 0; i < totalCount; i++) {
            final int index = i;
            final Object item = items.get(i);
            final String branchStartId = itemStartStepId;

            executorService.submit(() -> {
                try {
                    // ---- a) 复制上下文（barrier 引用被共享） ----
                    ExecutionContext branchCtx = context.copy(true);

                    // ---- b) 注入循环元数据（DSL 通过 $.{forStepId}.item 引用） ----
                    Map<String, Object> loopMeta = new HashMap<>();
                    loopMeta.put(PortNames.ITEM, item);
                    loopMeta.put(ContextKeys.INDEX, index);
                    loopMeta.put("totalCount", totalCount);
                    branchCtx.setVar(step.getId(), loopMeta);

                    log.debug("ForStep [{}]: 分支[{}] 开始执行, item={}", step.getId(), index, item);

                    // ---- c) 全程执行（不 stopAtJoinNodes），直到 CollectStep 或流程末尾 ----
                    // runBranchFlow 内部使用 stopAtJoinNodes=false，让分支真正到达 CollectStep。
                    // CollectStepExecutor 决定：
                    //   - 非最后线程 → return null → runBranchFlow 提前退出
                    //   - 最后线程   → 在 CollectStepExecutor 内部调用 engine.runBranchFlow
                    //                  继续执行 CollectStep 的下游节点（"线程接力"）
                    //                  → CollectStepExecutor 也 return null
                    engine.runBranchFlow(branchStartId, branchCtx, flow);

                    log.debug("ForStep [{}]: 分支[{}] runBranchFlow 已退出", step.getId(), index);

                } catch (Exception e) {
                    // ---- 分支异常补偿（特殊情况 B）----
                    log.error("ForStep [{}]: 分支[{}] 执行异常: {}",
                            step.getId(), index, e.getMessage(), e);
                    // 向屏障提交 ERROR 哨兵，确保计数器递增，防止 CollectStep 永久挂起
                    barrier.submitError(index, e);
                }
            });
        }

        // ====================================================================
        // Step 6: 【核心】主线程 return null
        //
        // 此时分支任务已经在线程池中跑起来了（或排队等待）。
        // 主调用线程返回 null 后，FlowEngine.runFlow() 的 while 循环终止。
        // FlowEngine.execute() 随后会检测主上下文中的 LoopBarrier 并阻塞等待。
        // ====================================================================
        log.info("ForStep [{}]: 主线程退出（return null），{} 条分支异步执行中", step.getId(), totalCount);
        return null;
    }

    /**
     * 空数组旁路：无需任何分支，直接触发 CollectStep 完成下游
     */
    private void triggerEmptyCollect(ForStep step, String collectStepId,
                                      LoopBarrier barrier, ExecutionContext context,
                                      FlowDefinition flow) {
        // CollectStep 的结果先写入主上下文
        Map<String, Object> emptyResult = new HashMap<>();
        emptyResult.put(PortNames.LIST, Collections.emptyList());
        emptyResult.put(ContextKeys.COUNT, 0);
        emptyResult.put("hasError", false);
        if (collectStepId != null) {
            context.setVar(collectStepId, emptyResult);
        }

        // 尝试直接从 CollectStep 节点的下游开始执行（列表/完成 端口）
        if (collectStepId != null) {
            try {
                Step collectStep =
                        engine.findStep(collectStepId, flow);
                if (collectStep != null) {
                    // 优先找 list 端口，其次找 finish 端口
                    Object listTarget = collectStep.getNext().get("list");
                    Object finishTarget = collectStep.getNext().get("finish");
                    String firstDownstream = resolveFirstStepId(listTarget != null ? listTarget : finishTarget);
                    if (firstDownstream != null) {
                        log.info("ForStep [{}]: 空数组旁路，从 CollectStep 下游 [{}] 继续", step.getId(), firstDownstream);
                        engine.runBranchFlow(firstDownstream, context, flow);
                    }
                }
            } catch (Exception e) {
                log.warn("ForStep [{}]: 空数组旁路触发下游失败: {}", step.getId(), e.getMessage());
            }
        }

        // 标记 barrier 为完成（completionFuture 已设置，engine.execute() 可以读取）
        barrier.forceComplete(Collections.emptyList());
    }

    /**
     * 解析 next 目标中的第一个步骤 ID
     */
    private String resolveFirstStepId(Object nextTarget) {
        if (nextTarget instanceof String) return (String) nextTarget;
        if (nextTarget instanceof List && !((List<?>) nextTarget).isEmpty()) {
            return ((List<?>) nextTarget).get(0).toString();
        }
        return null;
    }

    /**
     * 解析输入数组（支持 V3.1 inputs / 兼容旧版 collection）
     */
    @SuppressWarnings("unchecked")
    private List<?> resolveList(ForStep step, ExecutionContext context, FlowDefinition flow) {
        Map<String, Object> inputs = this.prepareInputs(step, context, flow);
        // 尝试 "list" key（ForStep 标准端口名）
        if (inputs.containsKey("list") && inputs.get("list") != null) {
            return toList(inputs.get("list"));
        }
        // 兼容 "collection" key
        if (inputs.containsKey("collection") && inputs.get("collection") != null) {
            return toList(inputs.get("collection"));
        }
        // 遍历所有 input 找第一个 List
        for (Object val : inputs.values()) {
            List<?> list = toList(val);
            if (list != null) return list;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<?> toList(Object val) {
        if (val instanceof List) return (List<?>) val;
        if (val instanceof Object[]) return Arrays.asList((Object[]) val);
        return null;
    }

    // ========================================================================
    // LoopBarrier - 全局并发屏障（线程安全）
    // ========================================================================

    /**
     * LoopBarrier - ForStep/CollectStep 之间的全局并发屏障
     *
     * <h2>设计思想：原子计数 + 无锁队列 + CompletableFuture</h2>
     *
     * <p>屏障的三个核心结构：</p>
     * <ul>
     *   <li>{@link #counter}：原子计数器，每条分支到达 CollectStep 时 incrementAndGet</li>
     *   <li>{@link #results}：无锁并发队列，存储每条分支的业务结果</li>
     *   <li>{@link #completionFuture}：多线程完成信号，engine.execute() 的主线程在此阻塞等待</li>
     * </ul>
     *
     * <h2>原子性保证</h2>
     * <p>利用 {@code AtomicInteger.incrementAndGet()} 的原子返回值精确判断"最后一条线程"：</p>
     * <pre>
     *   int count = counter.incrementAndGet();
     *   if (count &lt; totalCount) { return null; }   // 不是最后 → 静默终止
     *   if (count == totalCount) { ... }               // 恰好是最后 → 线程接力
     * </pre>
     * <p>此判断无需 synchronized，因为 AtomicInteger 的每次 incrementAndGet 保证唯一返回值，
     * 不会有两个线程同时得到 == totalCount 的结果。</p>
     *
     * <h2>竞态安全说明</h2>
     * <p>ConcurrentLinkedQueue.offer() 本身是线程安全的。最后一条线程将队列转为 List 时，
     * 由于所有其他线程已完成（count 已达到 totalCount），队列此时不会再有新写入，
     * 因此转换操作无需加锁。</p>
     *
     * <h2>上下文共享机制</h2>
     * <p>LoopBarrier 实例存储在 ExecutionContext 的 var Map 中。由于
     * {@code ExecutionContext.deepCopyIfNeeded()} 只对 Map 类型做深拷贝，
     * 对非 Map 对象直接返回引用，因此所有通过 {@code context.copy(true)} 创建的
     * 分支上下文，其 {@code var[BARRIER_KEY]} 均指向<b>同一个 LoopBarrier 实例</b>。</p>
     */
    public static class LoopBarrier {
        /** ForStep 节点 ID */
        public final String forStepId;
        /** 配对的 CollectStep 节点 ID */
        public final String collectStepId;
        /** 总分支数 */
        public final int totalCount;
        /** 超时时间（毫秒） */
        public final long timeoutMs;
        /** 指向主上下文的引用（非副本！用于最后一条线程将结果写回主流程） */
        public final ExecutionContext mainContext;

        /** 原子计数器：每条分支到达 CollectStep 时 +1 */
        public final AtomicInteger counter = new AtomicInteger(0);
        /** 无锁并发队列：存储每条分支的业务结果 */
        public final ConcurrentLinkedQueue<Object> results = new ConcurrentLinkedQueue<>();
        /** 完成信号：主线程在此阻塞等待，最后一条线程在完成下游后 complete() */
        public final CompletableFuture<Void> completionFuture = new CompletableFuture<>();
        /** 是否有分支发生异常 */
        public volatile boolean hasError = false;

        public LoopBarrier(String forStepId, String collectStepId, int totalCount,
                            ExecutionContext mainContext, long timeoutMs) {
            this.forStepId = forStepId;
            this.collectStepId = collectStepId;
            this.totalCount = totalCount;
            this.mainContext = mainContext;
            this.timeoutMs = timeoutMs;

            // 空数组：立即完成
            if (totalCount == 0) {
                completionFuture.complete(null);
            }
        }

        /**
         * 强制立即完成（用于空数组旁路或超时场景）
         */
        public void forceComplete(List<Object> partialResults) {
            if (!completionFuture.isDone()) {
                completionFuture.complete(null);
            }
        }

        /**
         * 提交分支异常哨兵（特殊情况 B：某条分支执行抛异常，防止 CollectStep 永久挂起）
         */
        public void submitError(int index, Throwable cause) {
            hasError = true;
            Map<String, Object> errorItem = new HashMap<>();
            errorItem.put("__type__", "SCATTER_GATHER_ERROR");
            errorItem.put(ContextKeys.INDEX, index);
            errorItem.put("errorMsg", cause != null ? cause.getMessage() : "unknown");
            results.offer(errorItem);
            // 让计数器前进，使 "最后一条线程" 的判断逻辑正常工作
            int count = counter.incrementAndGet();
            if (count >= totalCount && !completionFuture.isDone()) {
                // 所有分支（含异常分支）均已到达 → 强制完成（CollectStep 可能已经完成了）
                completionFuture.complete(null);
            }
        }

        /**
         * 主线程阻塞等待所有分支完成（在 engine.execute() 中调用）
         */
        public void awaitCompletion(long timeoutMs) throws Exception {
            try {
                completionFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("LoopBarrier [for={}, collect={}]: 等待超时 ({}ms), 已收 {}/{}",
                        forStepId, collectStepId, timeoutMs, counter.get(), totalCount);
                forceComplete(new ArrayList<>(results));
            }
        }

        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger(LoopBarrier.class);
    }
}
