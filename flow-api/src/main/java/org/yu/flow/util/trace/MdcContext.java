package org.yu.flow.util.trace;

import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 把提交线程的 MDC（主要是请求 ID）带到线程池里执行的任务上，任务结束后还原工作线程原来的 MDC。
 */
public final class MdcContext {

    private MdcContext() {
    }

    public static Runnable wrap(Runnable task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                task.run();
            } finally {
                apply(previous);
            }
        };
    }

    public static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(captured);
            try {
                return task.call();
            } finally {
                apply(previous);
            }
        };
    }

    /**
     * 包一层只负责传递 MDC 的执行器；生命周期方法原样转给底层线程池，关闭仍由池的所有者负责。
     */
    public static ExecutorService wrap(ExecutorService delegate) {
        if (delegate == null || delegate instanceof MdcExecutorService) {
            return delegate;
        }
        return new MdcExecutorService(delegate);
    }

    private static void apply(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    private static final class MdcExecutorService extends AbstractExecutorService {

        private final ExecutorService delegate;

        private MdcExecutorService(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(wrap(command));
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }
}
