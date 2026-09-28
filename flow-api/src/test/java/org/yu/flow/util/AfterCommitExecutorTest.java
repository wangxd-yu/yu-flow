package org.yu.flow.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfterCommitExecutorTest {

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void runsImmediatelyWithoutTransaction() {
        List<String> calls = new ArrayList<>();
        AfterCommitExecutor.run("t", () -> calls.add("a"));
        assertEquals(List.of("a"), calls);
    }

    @Test
    void defersUntilCommitAndKeepsOrder() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> calls = new ArrayList<>();
        AfterCommitExecutor.run("cancel", () -> calls.add("cancel"));
        AfterCommitExecutor.run("schedule", () -> calls.add("schedule"));
        assertTrue(calls.isEmpty());

        commit();
        assertEquals(List.of("cancel", "schedule"), calls);
    }

    @Test
    void skipsOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> calls = new ArrayList<>();
        AfterCommitExecutor.run("t", () -> calls.add("a"));

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationUtils.invokeAfterCompletion(syncs, TransactionSynchronization.STATUS_ROLLED_BACK);
        assertTrue(calls.isEmpty());
    }

    @Test
    void failureDoesNotBlockLaterCallbacks() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> calls = new ArrayList<>();
        AfterCommitExecutor.run("boom", () -> {
            throw new IllegalStateException("subscribe failed");
        });
        AfterCommitExecutor.run("next", () -> calls.add("next"));

        commit();
        assertEquals(List.of("next"), calls);
    }

    @Test
    void nestedCallDuringAfterCommitRunsImmediately() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> calls = new ArrayList<>();
        AfterCommitExecutor.run("outer", () -> {
            calls.add("outer");
            AfterCommitExecutor.run("inner", () -> calls.add("inner"));
        });

        commit();
        assertEquals(List.of("outer", "inner"), calls);
    }

    @Test
    void runOnceDeduplicatesWithinTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> calls = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            AfterCommitExecutor.runOnce("route refresh", () -> calls.add("route"));
            AfterCommitExecutor.runOnce("index rebuild", () -> calls.add("index"));
            AfterCommitExecutor.run("subscribe " + i, () -> calls.add("subscribe"));
        }
        assertTrue(calls.isEmpty());

        commit();
        assertEquals(1, calls.stream().filter("route"::equals).count());
        assertEquals(1, calls.stream().filter("index"::equals).count());
        assertEquals(50, calls.stream().filter("subscribe"::equals).count());
    }

    @Test
    void runOnceRunsImmediatelyWithoutTransaction() {
        List<String> calls = new ArrayList<>();
        AfterCommitExecutor.runOnce("k", () -> calls.add("a"));
        AfterCommitExecutor.runOnce("k", () -> calls.add("a"));
        assertEquals(List.of("a", "a"), calls);
    }

    private static void commit() {
        TransactionSynchronizationUtils.triggerAfterCommit();
    }
}
