package org.yu.flow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 运行时副作用（调度注册、MQ 订阅、本地缓存失效等）的提交后执行器。
 *
 * <p>在事务内调用时推迟到事务提交后执行，事务回滚则不执行，保证运行时状态与库一致；
 * 不在事务内时立即执行。</p>
 *
 * <p>提交后执行的动作只记日志、不抛异常：此时数据已落库无法回滚，而 Spring 在同一批
 * afterCommit 回调中遇到异常会跳过后续回调，整批发布时一个失败会连带其它资产不生效。</p>
 *
 * <p>afterCommit 回调里再注册的同步器不会被执行，因此回调执行期间嵌套调用本类会直接执行。</p>
 */
@Slf4j
public final class AfterCommitExecutor {

    private static final ThreadLocal<Boolean> RUNNING_AFTER_COMMIT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AfterCommitExecutor() {
    }

    /**
     * @param label  日志标识，便于定位失败的动作
     * @param action 运行时副作用
     */
    public static void run(String label, Runnable action) {
        if (RUNNING_AFTER_COMMIT.get() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new AfterCommitAction(label, action));
    }

    /**
     * 同一事务内按 key 只执行一次：整批导入、批量发布时，路由刷新、引用索引重建这类全量动作
     * 每次调用都注册会在提交后重复执行 N 次（并向集群广播 N 次）。
     */
    public static void runOnce(String key, Runnable action) {
        if (RUNNING_AFTER_COMMIT.get() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            if (s instanceof OnceAction once && once.key.equals(key)) {
                return;
            }
        }
        TransactionSynchronizationManager.registerSynchronization(new OnceAction(key, action));
    }

    private static void runGuarded(String label, Runnable action) {
        RUNNING_AFTER_COMMIT.set(Boolean.TRUE);
        try {
            action.run();
        } catch (Exception e) {
            log.error("[AfterCommit] 事务已提交，运行时刷新失败: {}", label, e);
        } finally {
            RUNNING_AFTER_COMMIT.remove();
        }
    }

    private static class AfterCommitAction implements TransactionSynchronization {
        final String label;
        final Runnable action;

        AfterCommitAction(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }

        @Override
        public void afterCommit() {
            runGuarded(label, action);
        }
    }

    private static final class OnceAction extends AfterCommitAction {
        final String key;

        OnceAction(String key, Runnable action) {
            super(key, action);
            this.key = key;
        }
    }
}
