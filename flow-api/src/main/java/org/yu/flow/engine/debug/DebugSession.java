package org.yu.flow.engine.debug;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yu.flow.engine.model.FlowTrace;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 调试会话实体。
 *
 * <p>负责管理一次交互式调试的全部生命周期状态，包括断点集合、
 * 线程挂起/唤醒机制、当前挂起节点的变量快照以及最终 Trace 报告。</p>
 *
 * <h3>线程模型</h3>
 * <ul>
 *   <li><b>引擎线程</b>：在后台异步执行 FlowEngine.execute()，每步执行前调用
 *       {@link #checkAndSuspend(String, Map)} 检查是否需要挂起。</li>
 *   <li><b>HTTP 线程</b>：通过 {@link #resume(Map)} 或 {@link #stepOver(Map)}
 *       从 REST 端点唤醒被挂起的引擎线程。</li>
 * </ul>
 *
 * <h3>挂起/唤醒原理</h3>
 * <p>每次挂起时创建一个新的 {@link CountDownLatch}(1)，引擎线程调用 {@code latch.await()}
 * 进入阻塞；Controller 线程调用 {@code latch.countDown()} 实现唤醒。
 * 相比 Object.wait/notify，CountDownLatch 更安全，不依赖 synchronized 块。</p>
 *
 * @author yu-flow
 * @since 1.0
 */
@Slf4j
public class DebugSession {

    /**
     * 会话状态枚举
     */
    public enum Status {
        /** 引擎正在运行中 */
        RUNNING,
        /** 引擎已在某个断点挂起，等待前端指令 */
        SUSPENDED,
        /** 引擎执行完毕（成功或出错） */
        COMPLETED,
        /** 会话已被取消 */
        CANCELLED
    }

    // ────────────── 不可变字段 ──────────────

    /** 会话唯一标识 */
    @Getter
    private final String sessionId;

    /** 断点集合（节点 ID） */
    @Getter
    private final Set<String> breakpoints;

    /** 会话创建时间戳 */
    @Getter
    private final long createTime;

    /** 调试超时时间（毫秒），默认 5 分钟 */
    @Getter
    private final long timeoutMs;

    // ────────────── 可变状态（volatile 保证跨线程可见性） ──────────────

    /** 当前会话状态 */
    @Getter
    private volatile Status status = Status.RUNNING;

    /** 当前挂起的节点 ID */
    @Getter
    private volatile String suspendedNodeId;

    /** 当前挂起的节点名称 */
    @Getter
    private volatile String suspendedNodeName;

    /** 当前挂起时刻的上下文变量快照 */
    @Getter
    private volatile Map<String, Object> suspendedVariables;

    /** 单步模式标记：为 true 时，下一个节点执行前也自动挂起 */
    private volatile boolean stepOverMode = false;

    /** 最终的 FlowTrace 执行报告 */
    @Getter
    private volatile FlowTrace finalTrace;

    /** 终态的错误信息（如果有） */
    @Getter
    private volatile String errorMessage;

    /** 前端提交的变量修改（在 resume 时注入回 ExecutionContext） */
    @Getter
    private volatile Map<String, Object> pendingVariableUpdates;

    // ────────────── 线程同步原语 ──────────────

    /**
     * 当前活跃的门闩。每次挂起创建新实例，唤醒后废弃。
     * volatile 保证引擎线程读到最新的引用。
     */
    private volatile CountDownLatch suspendLatch;

    // ════════════════════════════════════════════════════════════════

    public DebugSession(String sessionId, Set<String> breakpoints) {
        this(sessionId, breakpoints, 5 * 60 * 1000L); // 默认 5 分钟超时
    }

    public DebugSession(String sessionId, Set<String> breakpoints, long timeoutMs) {
        this.sessionId = sessionId;
        this.breakpoints = breakpoints != null
                ? Collections.unmodifiableSet(new HashSet<>(breakpoints))
                : Collections.emptySet();
        this.createTime = System.currentTimeMillis();
        this.timeoutMs = timeoutMs;
    }

    // ════════════════════════════ 引擎线程侧方法 ════════════════════════════

    /**
     * 在 FlowEngine.executeStep() <b>执行节点业务逻辑之前</b>调用。
     *
     * <p>如果当前节点命中断点（或处于单步模式），引擎线程将在此方法内阻塞，
     * 直到前端调用 {@link #resume} 或 {@link #stepOver} 唤醒。</p>
     *
     * @param nodeId        当前即将执行的节点 ID
     * @param currentVars   当前 ExecutionContext 的完整变量映射（深拷贝后传入）
     * @return 如果挂起并恢复后，前端有提交变量修改则返回该修改映射，否则返回 null
     */
    public Map<String, Object> checkAndSuspend(String nodeId, String nodeName, Map<String, Object> currentVars) {
        if (status == Status.CANCELLED || status == Status.COMPLETED) {
            return null;
        }

        boolean shouldSuspend = stepOverMode || breakpoints.contains(nodeId);
        if (!shouldSuspend) {
            return null;
        }

        // 重置单步模式标记（一次性消费）
        stepOverMode = false;

        // ── 挂起 ──
        this.suspendedNodeId = nodeId;
        this.suspendedNodeName = nodeName;
        this.suspendedVariables = currentVars; // 调用方应传入深拷贝
        this.pendingVariableUpdates = null;
        this.suspendLatch = new CountDownLatch(1);
        this.status = Status.SUSPENDED;

        log.info("[DebugSession-{}] 引擎线程在节点 [{}]({}) 挂起，等待前端指令...",
                sessionId, nodeId, nodeName);

        try {
            // 阻塞引擎线程，等待前端唤醒或超时
            boolean awoken = suspendLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!awoken) {
                log.warn("[DebugSession-{}] 调试超时（{}ms），自动恢复执行", sessionId, timeoutMs);
                this.status = Status.RUNNING;
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[DebugSession-{}] 引擎线程被中断", sessionId);
            this.status = Status.CANCELLED;
            return null;
        }

        // ── 唤醒后 ──
        if (status == Status.CANCELLED) {
            return null;
        }
        this.status = Status.RUNNING;

        // 返回前端提交的变量修改（可能为 null）
        Map<String, Object> updates = this.pendingVariableUpdates;
        this.pendingVariableUpdates = null;
        return updates;
    }

    /**
     * 引擎执行完毕时调用，标记会话为已完成并存储最终 Trace 报告。
     *
     * @param trace 最终 FlowTrace
     */
    public void markCompleted(FlowTrace trace) {
        this.finalTrace = trace;
        this.status = Status.COMPLETED;
        this.suspendedNodeId = null;
        this.suspendedVariables = null;
        log.info("[DebugSession-{}] 执行完毕，状态={}", sessionId,
                trace != null ? trace.getStatus() : "null");
    }

    /**
     * 引擎执行出错时调用。
     */
    public void markError(String errorMsg, FlowTrace trace) {
        this.errorMessage = errorMsg;
        this.finalTrace = trace;
        this.status = Status.COMPLETED;
        this.suspendedNodeId = null;
        this.suspendedVariables = null;
        log.error("[DebugSession-{}] 执行出错: {}", sessionId, errorMsg);
    }

    // ════════════════════════════ HTTP 线程侧方法 ════════════════════════════

    /**
     * 恢复执行（Continue / F8）。引擎线程将一直运行到下一个断点或流程结束。
     *
     * @param variableUpdates 可选的变量修改映射（前端用户在调试面板中篡改的变量），null 表示不修改
     */
    public void resume(Map<String, Object> variableUpdates) {
        if (status != Status.SUSPENDED) {
            log.warn("[DebugSession-{}] 尝试 resume 但状态不是 SUSPENDED (当前: {})", sessionId, status);
            return;
        }
        this.pendingVariableUpdates = variableUpdates;
        this.stepOverMode = false;
        log.info("[DebugSession-{}] 收到 Resume 指令，唤醒引擎线程", sessionId);
        doResume();
    }

    /**
     * 单步跳过（Step Over / F6）。唤醒引擎执行当前挂起节点的业务逻辑，
     * 然后在下一个节点的入口处再次自动挂起。
     *
     * @param variableUpdates 可选的变量修改
     */
    public void stepOver(Map<String, Object> variableUpdates) {
        if (status != Status.SUSPENDED) {
            log.warn("[DebugSession-{}] 尝试 stepOver 但状态不是 SUSPENDED (当前: {})", sessionId, status);
            return;
        }
        this.pendingVariableUpdates = variableUpdates;
        this.stepOverMode = true;
        log.info("[DebugSession-{}] 收到 StepOver 指令，唤醒引擎线程并设置单步标记", sessionId);
        doResume();
    }

    /**
     * 取消调试会话。如果引擎线程正在挂起，则唤醒它使其退出。
     */
    public void cancel() {
        this.status = Status.CANCELLED;
        CountDownLatch latch = this.suspendLatch;
        if (latch != null) {
            latch.countDown();
        }
        log.info("[DebugSession-{}] 会话已取消", sessionId);
    }

    // ════════════════════════════ 私有方法 ════════════════════════════

    private void doResume() {
        CountDownLatch latch = this.suspendLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * 检查会话是否已超时（用于定期清理）
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createTime > timeoutMs * 2;
    }
}
