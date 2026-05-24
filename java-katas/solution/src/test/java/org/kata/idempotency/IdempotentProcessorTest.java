package org.kata.idempotency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class IdempotentProcessorTest {

    @Test
    void action_runs_once_for_first_call() {
        var processor = new IdempotentProcessor();
        var callCount = new AtomicInteger();

        String result = processor.process("key-1", () -> {
            callCount.incrementAndGet();
            return "value-1";
        });

        assertEquals("value-1", result);
        assertEquals(1, callCount.get());
    }

    @Test
    void repeated_calls_with_same_key_return_cached_result_without_running_action_again() {
        var processor = new IdempotentProcessor();
        var callCount = new AtomicInteger();

        // First call: action runs.
        String first = processor.process("key-a", () -> {
            callCount.incrementAndGet();
            return "result";
        });

        // Subsequent calls with same key: action must NOT run, cached value returned.
        String second = processor.process("key-a", () -> {
            callCount.incrementAndGet();
            return "should-never-be-returned";
        });
        String third = processor.process("key-a", () -> "also-never");

        assertEquals("result", first);
        assertEquals("result", second,  "second call must return cached value");
        assertEquals("result", third,   "third call must return cached value");
        assertEquals(1, callCount.get(), "action must run exactly once");
    }

    @Test
    void different_keys_run_independently() {
        var processor = new IdempotentProcessor();
        var counter = new AtomicInteger();

        String r1 = processor.process("key-x", () -> { counter.incrementAndGet(); return "x"; });
        String r2 = processor.process("key-y", () -> { counter.incrementAndGet(); return "y"; });
        String r3 = processor.process("key-z", () -> { counter.incrementAndGet(); return "z"; });

        assertEquals("x", r1);
        assertEquals("y", r2);
        assertEquals("z", r3);
        assertEquals(3, counter.get(), "one execution per distinct key");
    }

    @Test
    void is_processed_reflects_cache_state() {
        var processor = new IdempotentProcessor();
        assertFalse(processor.isProcessed("new-key"));
        processor.process("new-key", () -> "done");
        assertTrue(processor.isProcessed("new-key"));
    }

    @Test
    void null_key_throws_illegal_argument() {
        var processor = new IdempotentProcessor();
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(null, () -> "value"));
    }

    @Test
    void null_action_throws_illegal_argument() {
        var processor = new IdempotentProcessor();
        assertThrows(IllegalArgumentException.class,
                () -> processor.process("key", null));
    }

    @Test
    void concurrent_calls_with_same_key_execute_action_exactly_once() throws Exception {
        // This is the critical concurrency test. N threads all submit the same idempotency key
        // simultaneously. computeIfAbsent must ensure the action runs exactly once despite the
        // race, and every caller must receive the same result.
        var processor = new IdempotentProcessor();
        var actionExecutionCount = new AtomicInteger();
        var results = new CopyOnWriteArrayList<String>();

        int N = 200;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    String result = processor.process("shared-key", () -> {
                        actionExecutionCount.incrementAndGet();
                        return "computed-once";
                    });
                    results.add(result);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "test timed out — possible deadlock");
        }

        // The action must have run exactly once regardless of how many threads raced.
        assertEquals(1, actionExecutionCount.get(),
                "action must execute exactly once even under concurrent first-touch");

        // Every caller must have received the same cached result.
        assertEquals(N, results.size(), "every thread must have received a result");
        assertTrue(results.stream().allMatch("computed-once"::equals),
                "all concurrent callers must receive the same result");
    }

    @Test
    void concurrent_calls_with_distinct_keys_each_execute_once() throws Exception {
        // Each thread uses a unique key — every action should run exactly once, and all N
        // results should be distinct. Validates that per-key isolation is maintained under
        // concurrent access to the shared ConcurrentHashMap.
        var processor = new IdempotentProcessor();
        var totalExecutions = new AtomicInteger();
        var results = new CopyOnWriteArrayList<String>();

        int N = 100;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    String key = "key-" + i;
                    String result = processor.process(key, () -> {
                        totalExecutions.incrementAndGet();
                        return "result-" + i;
                    });
                    results.add(result);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(N, totalExecutions.get(), "each unique key must execute its action once");
        assertEquals(N, results.size());
    }
}
