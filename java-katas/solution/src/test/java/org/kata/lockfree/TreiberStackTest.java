package org.kata.lockfree;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TreiberStack}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Single-threaded LIFO semantics — the basic contract of a stack.</li>
 *   <li>Empty-pop behaviour — must return Optional.empty(), not throw.</li>
 *   <li>Null-push rejection — enforced to allow Optional.of() in pop without NPE.</li>
 *   <li>Virtual-thread stress test — N producers push K distinct values each; N consumers
 *       pop until all values are accounted for. Asserts that the multiset of popped values
 *       exactly equals the multiset pushed: no lost items, no duplicates, no extra items.
 *       This exercises the CAS-loop under real concurrent contention.</li>
 * </ul>
 */
class TreiberStackTest {

    // ---- Single-threaded correctness ----

    @Test
    void lifo_order_single_threaded() {
        var stack = new TreiberStack<Integer>();
        stack.push(1);
        stack.push(2);
        stack.push(3);

        // Stack is LIFO: 3 was pushed last so it is popped first.
        assertEquals(3, stack.pop().orElseThrow());
        assertEquals(2, stack.pop().orElseThrow());
        assertEquals(1, stack.pop().orElseThrow());
    }

    @Test
    void pop_on_empty_returns_empty_optional() {
        var stack = new TreiberStack<String>();
        assertTrue(stack.pop().isEmpty(), "pop on empty stack must return Optional.empty()");
    }

    @Test
    void is_empty_reflects_state() {
        var stack = new TreiberStack<Integer>();
        assertTrue(stack.isEmpty());
        stack.push(42);
        assertFalse(stack.isEmpty());
        stack.pop();
        assertTrue(stack.isEmpty());
    }

    @Test
    void null_push_rejected_with_npe() {
        var stack = new TreiberStack<String>();
        assertThrows(NullPointerException.class, () -> stack.push(null));
    }

    @Test
    void single_element_push_then_pop() {
        var stack = new TreiberStack<String>();
        stack.push("hello");
        assertEquals("hello", stack.pop().orElseThrow());
        assertTrue(stack.isEmpty());
    }

    // ---- Concurrent stress test ----

    /**
     * Virtual-thread stress test: conservation of elements under concurrent push and pop.
     *
     * <p>N_PRODUCERS threads each push N_PER_THREAD distinct integer values. Each producer
     * uses a unique offset so that every pushed integer is globally unique: producer {@code p}
     * pushes values in {@code [p * N_PER_THREAD, (p+1) * N_PER_THREAD)}.
     *
     * <p>N_CONSUMERS threads each pop items until a shared atomic budget ({@code budget}) is
     * exhausted. The budget is pre-loaded to {@code total = N_PRODUCERS * N_PER_THREAD}. Each
     * consumer decrements the budget via {@code getAndDecrement()}; if the value before
     * decrement is positive the consumer has claimed one pop slot and must pop exactly one item.
     * Because producers and consumers run concurrently, a consumer may transiently find the
     * stack empty and must spin-yield until a producer pushes something.
     *
     * <p>After all threads finish we assert:
     * <ul>
     *   <li>Exactly {@code total} values were collected (no items lost or created).</li>
     *   <li>Every collected value is in the expected range {@code [0, total)}.</li>
     *   <li>Every pushed value appears exactly once (no duplicates from a botched CAS).</li>
     * </ul>
     *
     * <p>A {@link CountDownLatch gate} releases all threads simultaneously to maximise
     * contention at start-up. A second latch {@code done} with count
     * {@code N_PRODUCERS + N_CONSUMERS} is decremented by each thread on exit so the main
     * thread can await completion.
     */
    @Test
    void concurrent_push_pop_no_lost_or_duplicated_items() throws InterruptedException {
        final int N_PRODUCERS = 8;
        final int N_CONSUMERS = 8;
        final int N_PER_THREAD = 500;
        final int total = N_PRODUCERS * N_PER_THREAD;

        var stack = new TreiberStack<Integer>();

        // gate: countdown to 0 to release all threads simultaneously for maximum contention
        var gate = new CountDownLatch(1);
        // done: one count per producer + consumer thread; main thread awaits completion
        var done = new CountDownLatch(N_PRODUCERS + N_CONSUMERS);

        // Thread-safe collection to gather all popped values for post-run assertion.
        var popped = new ConcurrentLinkedQueue<Integer>();

        // Shared pop budget. Each consumer claims a slot via getAndDecrement(). If the
        // returned value is > 0, the consumer owns a slot and must pop one item.
        // This ensures exactly `total` pops occur in aggregate regardless of how threads
        // are scheduled, without requiring any producer/consumer rendezvous protocol.
        var budget = new AtomicInteger(total);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {

            // --- Producers ---
            // Each producer pushes N_PER_THREAD unique integers starting from its offset.
            // All push() calls use the Treiber CAS loop internally; no external locking needed.
            for (int p = 0; p < N_PRODUCERS; p++) {
                final int offset = p * N_PER_THREAD;
                exec.submit(() -> {
                    try {
                        gate.await(); // barrier: wait for all threads to be ready
                        for (int j = 0; j < N_PER_THREAD; j++) {
                            stack.push(offset + j);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // --- Consumers ---
            // Each consumer claims pop slots by decrementing the budget. If it claims a slot
            // (budget was > 0 before decrement) it must pop exactly one item, spinning on
            // Optional.empty() until a producer makes one available. Thread.yield() hands
            // the virtual-thread scheduler a hint to switch to another virtual thread (likely
            // a producer), avoiding a hot busy-wait that would starve producers.
            for (int c = 0; c < N_CONSUMERS; c++) {
                exec.submit(() -> {
                    try {
                        gate.await();
                        while (budget.getAndDecrement() > 0) {
                            Integer item;
                            do {
                                item = stack.pop().orElse(null);
                                if (item == null) Thread.yield();
                            } while (item == null);
                            popped.offer(item);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            gate.countDown(); // open the floodgate — all threads start concurrently
            assertTrue(done.await(10, TimeUnit.SECONDS),
                    "threads did not complete within 10 seconds");
        }

        // ---- Post-run assertions ----

        // (a) Total count must match exactly: no items were lost or created out of thin air.
        assertEquals(total, popped.size(),
                "total popped count must equal total pushed count");

        // (b) Build a frequency map. Every value in [0, total) should appear exactly once.
        //     Any duplicate signals a CAS bug where two consumers both saw the same node as head.
        //     Any missing value signals a lost item where a push node was never reachable from head.
        var frequency = new HashMap<Integer, Integer>();
        for (Integer v : popped) {
            assertNotNull(v, "popped item must not be null");
            assertTrue(v >= 0 && v < total,
                    "popped value " + v + " is outside the expected range [0, " + total + ")");
            frequency.merge(v, 1, Integer::sum);
        }

        // Every distinct value seen should equal total: confirms no value appeared twice.
        assertEquals(total, frequency.size(),
                "number of distinct popped values must equal total — duplicates indicate a CAS bug");

        for (var entry : frequency.entrySet()) {
            assertEquals(1, entry.getValue(),
                    "value " + entry.getKey() + " was popped " + entry.getValue() + " times (expected 1)");
        }
    }
}
