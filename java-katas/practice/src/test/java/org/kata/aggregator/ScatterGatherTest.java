package org.kata.aggregator;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ScatterGatherTest {

    // ---- gatherAll tests ----

    @Test
    void gather_all_returns_all_results_from_successful_tasks() {
        // Three tasks each return a distinct integer; the gathered list must contain all three.
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());

        List<Supplier<Integer>> tasks = List.of(
                () -> 1,
                () -> 2,
                () -> 3
        );

        List<Integer> results = sg.gatherAll(tasks).join();
        // Order is guaranteed to match the order of tasks (index-aligned scatter).
        assertEquals(List.of(1, 2, 3), results);
    }

    @Test
    void gather_all_preserves_result_order() {
        // Even if tasks finish in a different order (virtual threads schedule non-deterministically),
        // the result list must be index-aligned with the input task list.
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());

        // Tasks return their index; a slow first task still maps to index 0 in the output.
        List<Supplier<String>> tasks = List.of(
                () -> { sleepMs(10); return "slow"; },
                () -> "fast-a",
                () -> "fast-b"
        );

        List<String> results = sg.gatherAll(tasks).join();
        assertEquals("slow",   results.get(0));
        assertEquals("fast-a", results.get(1));
        assertEquals("fast-b", results.get(2));
    }

    @Test
    void gather_all_fails_if_any_task_throws() {
        // One of three tasks throws. gatherAll must propagate the failure as a CompletionException
        // wrapping the original exception (this is CompletableFuture's standard wrapping).
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());

        List<Supplier<Integer>> tasks = List.of(
                () -> 1,
                () -> { throw new RuntimeException("task-2 failed"); },
                () -> 3
        );

        CompletableFuture<List<Integer>> future = sg.gatherAll(tasks);
        var ex = assertThrows(CompletionException.class, future::join);
        assertEquals("task-2 failed", ex.getCause().getMessage());
    }

    @Test
    void gather_all_works_with_empty_task_list() {
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());
        List<Integer> results = sg.<Integer>gatherAll(List.of()).join();
        assertTrue(results.isEmpty());
    }

    // ---- gatherAllWithTimeout tests ----

    @Test
    void gather_with_timeout_returns_results_of_fast_tasks_only() {
        // Two fast tasks and one slow task. The slow task should be dropped; fast tasks returned.
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());

        // The timeout is 50ms; the slow task takes ~200ms — it will be dropped.
        List<Supplier<String>> tasks = List.of(
                () -> "fast-1",
                () -> { sleepMs(200); return "slow"; },
                () -> "fast-2"
        );

        List<String> results = sg.gatherAllWithTimeout(tasks, Duration.ofMillis(50)).join();

        // We cannot guarantee the exact order of fast results (they race to a list),
        // so we assert membership via a Set.
        Set<String> resultSet = Set.copyOf(results);
        assertTrue(resultSet.contains("fast-1"),   "fast-1 should be included");
        assertTrue(resultSet.contains("fast-2"),   "fast-2 should be included");
        assertFalse(resultSet.contains("slow"),    "slow task must be dropped");
        assertEquals(2, results.size());
    }

    @Test
    void gather_with_timeout_returns_empty_when_all_tasks_are_slow() {
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());

        List<Supplier<String>> tasks = List.of(
                () -> { sleepMs(300); return "late-1"; },
                () -> { sleepMs(300); return "late-2"; }
        );

        List<String> results = sg.gatherAllWithTimeout(tasks, Duration.ofMillis(50)).join();
        assertTrue(results.isEmpty(), "all tasks timed out — result should be empty");
    }

    @Test
    void gather_with_timeout_drops_failed_tasks_and_keeps_successful_ones() {
        // A failing task (exception) must be treated the same as a timed-out task — dropped.
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());

        List<Supplier<String>> tasks = List.of(
                () -> "ok",
                () -> { throw new RuntimeException("boom"); }
        );

        // Large timeout so the throw happens before timeout — failure must still be dropped.
        List<String> results = sg.gatherAllWithTimeout(tasks, Duration.ofSeconds(5)).join();
        assertEquals(List.of("ok"), results);
    }

    @Test
    void gather_with_timeout_returns_all_when_all_tasks_are_fast() {
        var sg = new ScatterGather(Executors.newVirtualThreadPerTaskExecutor());

        List<Supplier<Integer>> tasks = List.of(
                () -> 10,
                () -> 20,
                () -> 30
        );

        List<Integer> results = sg.gatherAllWithTimeout(tasks, Duration.ofSeconds(5)).join();
        // Order may vary since partial gather is unordered, but all three must be present.
        assertEquals(Set.of(10, 20, 30), Set.copyOf(results));
    }

    // ---- helper ----

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
