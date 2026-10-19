package org.yu.flow.util.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdcContextTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void submitAndSupplyAsyncCarryRequestId() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            ExecutorService wrapped = MdcContext.wrap(pool);
            MDC.put(RequestIds.MDC_KEY, "req-1");

            assertEquals("req-1", wrapped.submit(RequestIds::current).get(5, TimeUnit.SECONDS));
            assertEquals("req-1", CompletableFuture.supplyAsync(RequestIds::current, wrapped)
                    .get(5, TimeUnit.SECONDS));

            MDC.put(RequestIds.MDC_KEY, "req-2");
            assertEquals("req-2", wrapped.submit(RequestIds::current).get(5, TimeUnit.SECONDS));

            // 同一个工作线程，没经过包装的任务不应残留上一个请求的 ID
            assertNull(pool.submit(RequestIds::current).get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void restoresWorkerContextAfterTask() throws Exception {
        MDC.put(RequestIds.MDC_KEY, "worker");
        Runnable task = MdcContext.wrap(() -> { });
        MDC.put(RequestIds.MDC_KEY, "worker-own");

        task.run();

        assertEquals("worker-own", RequestIds.current());
    }

    @Test
    void wrapperForwardsLifecycleAndIsIdempotent() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = MdcContext.wrap(pool);
        assertSame(wrapped, MdcContext.wrap(wrapped));

        wrapped.shutdown();
        assertTrue(pool.isShutdown());
        assertTrue(wrapped.awaitTermination(5, TimeUnit.SECONDS));
    }
}
