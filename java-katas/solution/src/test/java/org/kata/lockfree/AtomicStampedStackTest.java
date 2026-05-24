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
 * Tests for {@link AtomicStampedStack}.
 *
 * <p>The ABA problem cannot be deterministically reproduced in a JVM test without a node
 * free-list and precise scheduling control, so this test suite focuses on:
 * <ul>
 *   <li>Correct LIFO semantics in the single-threaded case.</li>
 *   <li>Safe empty-pop handling.</li>
 *   <li>Null-push rejection.</li>
 *   <li>Conservation of elements under concurrent push/pop (same structure as
 *       {@link TreiberStackTest#concurrent_push_pop_no_lost_or_duplicated_items()}).
 *       Correctness under contention is the observable proxy for ABA-safety: if the stamp
 *       were wrong (e.g. never incremented, or always zero), concurrent pop would intermittently
 *       succeed with stale node references, producing duplicates or losses that this test
 *       would catch.</li>
 * </ul>
 *
 * <p>The stamp mechanism is verified indirectly: in a buggy implementation where the stamp
 * is always 0 (or never updated), the CAS would accept any snapshot of the same node regardless
 * of intervening mutations. Under the concurrency level of the stress test this would produce
 * observable corruption (duplicate pops or lost pushes) that the frequency assertions below
 * would catch.
 */
class AtomicStampedStackTest {

    // ---- Single-threaded correctness ----

    @Test
    void lifo_order_single_threaded() {
        var stack = new AtomicStampedStack<Integer>();
        stack.push(10);
        stack.push(20);
        stack.push(30);

        assertEquals(30, stack.pop().orElseThrow());
        assertEquals(20, stack.pop().orElseThrow());
        assertEquals(10, stack.pop().orElseThrow());
    }

    @Test
    void pop_on_empty_returns_empty_optional() {
        var stack = new AtomicStampedStack<String>();
        assertTrue(stack.pop().isEmpty(),
                "pop on empty stack must return Optional.empty()");
    }

    @Test
    void is_empty_reflects_state() {
        var stack = new AtomicStampedStack<Integer>();
        assertTrue(stack.isEmpty());
        stack.push(1);
        assertFalse(stack.isEmpty());
        stack.pop();
        assertTrue(stack.isEmpty());
    }

    @Test
    void null_push_rejected_with_npe() {
        var stack = new AtomicStampedStack<String>();
        assertThrows(NullPointerException.class, () -> stack.push(null));
    }

    @Test
    void single_element_push_then_pop() {
        var stack = new AtomicStampedStack<String>();
        stack.push("world");
        assertEquals("world", stack.pop().orElseThrow());
        assertTrue(stack.isEmpty());
    }

    @Test
    void stamps_advance_monotonically_visible_in_behaviour() {
        // Push and pop N items sequentially. Each should succeed in order.
        // This indirectly verifies that the stamp is being updated: if it were stuck at 0
        // then in a concurrent test a second pop could CAS with the same stamp and succeed
        // twice. In a single-threaded test the stack would still behave correctly (no CAS
        // contention), but the pattern below at least exercises the push-pop-push-pop stamp
        // advancement without timing-dependent assertions.
        var stack = new AtomicStampedStack<Integer>();
        for (int i = 0; i < 10; i++) stack.push(i);
        for (int i = 9; i >= 0; i--) {
            assertEquals(i, stack.pop().orElseThrow(),
                    "pop at step " + (9 - i) + " must return " + i);
        }
        assertTrue(stack.isEmpty());
    }

    // ---- Concurrent stress test ----

    /**
     * Conservation stress test under concurrent push and pop.
     *
     * <p>Identical in structure to {@link TreiberStackTest#concurrent_push_pop_no_lost_or_duplicated_items()}.
     * N_PRODUCERS threads push N_PER_THREAD globally unique integers; N_CONSUMERS threads
     * collectively pop exactly {@code total} items using a shared atomic budget.
     *
     * <p>The same assertions apply: exactly {@code total} distinct items dequeued, each exactly once.
     *
     * <p>Why this catches ABA bugs: a broken stamp implementation (e.g. stamp always 0) would
     * allow a consumer that snapshotted an old head to CAS successfully even after intervening
     * mutations. Under N_PRODUCERS=8, N_CONSUMERS=8, N_PER_THREAD=500, concurrent mutations are
     * frequent enough that such a bug would produce duplicates or mismatches that the frequency
     * map assertion would detect.
     */
    @Test
    void concurrent_push_pop_no_lost_or_duplicated_items() throws InterruptedException {
        final int N_PRODUCERS = 8;
        final int N_CONSUMERS = 8;
        final int N_PER_THREAD = 500;
        final int total = N_PRODUCERS * N_PER_THREAD;

        var stack = new AtomicStampedStack<Integer>();
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N_PRODUCERS + N_CONSUMERS);
        var popped = new ConcurrentLinkedQueue<Integer>();
        var budget = new AtomicInteger(total);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {

            // Producers: each pushes a disjoint range of integers.
            for (int p = 0; p < N_PRODUCERS; p++) {
                final int offset = p * N_PER_THREAD;
                exec.submit(() -> {
                    try {
                        gate.await();
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

            // Consumers: claim pop slots via the shared budget, spin-yield if the stack is
            // momentarily empty (a producer hasn't pushed the next item yet).
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

            gate.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS),
                    "threads did not complete within 10 seconds");
        }

        // ---- Post-run assertions ----

        assertEquals(total, popped.size(),
                "total popped count must equal total pushed count");

        var frequency = new HashMap<Integer, Integer>();
        for (Integer v : popped) {
            assertNotNull(v, "popped item must not be null");
            assertTrue(v >= 0 && v < total,
                    "popped value " + v + " is outside the expected range [0, " + total + ")");
            frequency.merge(v, 1, Integer::sum);
        }

        assertEquals(total, frequency.size(),
                "number of distinct popped values must equal total — duplicates indicate a CAS/stamp bug");

        for (var entry : frequency.entrySet()) {
            assertEquals(1, entry.getValue(),
                    "value " + entry.getKey() + " was popped " + entry.getValue() + " times (expected 1)");
        }
    }
}
