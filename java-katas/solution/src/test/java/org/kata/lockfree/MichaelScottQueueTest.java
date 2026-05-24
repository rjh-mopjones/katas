package org.kata.lockfree;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MichaelScottQueue}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Single-threaded FIFO semantics — the basic ordering contract of a queue.</li>
 *   <li>Dequeue on empty — must return Optional.empty().</li>
 *   <li>Null-enqueue rejection.</li>
 *   <li>isEmpty() accuracy in single-threaded usage.</li>
 *   <li>Multi-producer multi-consumer stress test — every enqueued item is dequeued
 *       exactly once (no losses, no duplicates). This exercises the two-CAS enqueue,
 *       the tail-helping path, and the dequeue linearization under heavy contention.</li>
 * </ul>
 */
class MichaelScottQueueTest {

    // ---- Single-threaded correctness ----

    @Test
    void fifo_order_single_threaded() {
        var queue = new MichaelScottQueue<Integer>();
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);

        // Queue is FIFO: 1 was enqueued first so it is dequeued first.
        assertEquals(1, queue.dequeue().orElseThrow());
        assertEquals(2, queue.dequeue().orElseThrow());
        assertEquals(3, queue.dequeue().orElseThrow());
    }

    @Test
    void dequeue_on_empty_returns_empty_optional() {
        var queue = new MichaelScottQueue<String>();
        assertTrue(queue.dequeue().isEmpty(),
                "dequeue on empty queue must return Optional.empty()");
    }

    @Test
    void is_empty_reflects_state() {
        var queue = new MichaelScottQueue<Integer>();
        assertTrue(queue.isEmpty());
        queue.enqueue(1);
        assertFalse(queue.isEmpty());
        queue.dequeue();
        assertTrue(queue.isEmpty());
    }

    @Test
    void null_enqueue_rejected_with_npe() {
        var queue = new MichaelScottQueue<String>();
        assertThrows(NullPointerException.class, () -> queue.enqueue(null));
    }

    @Test
    void single_element_enqueue_then_dequeue() {
        var queue = new MichaelScottQueue<String>();
        queue.enqueue("hello");
        assertEquals("hello", queue.dequeue().orElseThrow());
        assertTrue(queue.isEmpty());
    }

    @Test
    void alternating_enqueue_dequeue_maintains_fifo() {
        // Interleaving enqueue and dequeue single-threaded is a common edge-case path
        // through the dummy-node logic: head and tail point to the same node briefly.
        var queue = new MichaelScottQueue<Integer>();
        queue.enqueue(10);
        assertEquals(10, queue.dequeue().orElseThrow()); // drains to empty
        queue.enqueue(20);
        queue.enqueue(30);
        assertEquals(20, queue.dequeue().orElseThrow());
        assertEquals(30, queue.dequeue().orElseThrow());
        assertTrue(queue.isEmpty());
    }

    // ---- Concurrent stress test ----

    /**
     * Multi-producer multi-consumer stress test: every enqueued item is dequeued exactly once.
     *
     * <p>N_PRODUCERS threads each enqueue N_PER_THREAD unique integers. Producer {@code p}
     * enqueues values in {@code [p * N_PER_THREAD, (p+1) * N_PER_THREAD)}, so every value is
     * globally unique across all producers.
     *
     * <p>N_CONSUMERS threads collectively dequeue exactly {@code total} items using a shared
     * {@link AtomicInteger} budget (same technique as the {@link TreiberStackTest}). Each
     * consumer that claims a slot spins until an item is available (the item may not have been
     * enqueued yet by the concurrent producer).
     *
     * <p>After all threads complete we assert:
     * <ul>
     *   <li>Exactly {@code total} items were dequeued.</li>
     *   <li>Every item is in the expected range {@code [0, total)}.</li>
     *   <li>Every item appears exactly once — no item was dequeued twice (a spurious CAS
     *       win would cause this) and no item was lost (a failed CAS that was not retried
     *       would cause this).</li>
     * </ul>
     *
     * <p>The test also indirectly exercises the tail-helping path: under concurrent enqueuers
     * it is likely that some threads will observe a lagging tail (tail.next != null) and
     * help advance it before performing their own enqueue.
     */
    @Test
    void concurrent_enqueue_dequeue_every_item_dequeued_exactly_once() throws InterruptedException {
        final int N_PRODUCERS = 8;
        final int N_CONSUMERS = 8;
        final int N_PER_THREAD = 500;
        final int total = N_PRODUCERS * N_PER_THREAD;

        var queue = new MichaelScottQueue<Integer>();
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N_PRODUCERS + N_CONSUMERS);

        // Frequency table: maps each value to the number of times it was dequeued.
        // ConcurrentHashMap with AtomicInteger values allows concurrent increment without
        // external locking. We merge into a plain HashMap for assertion after all threads finish.
        var seen = new ConcurrentHashMap<Integer, AtomicInteger>();

        // Shared dequeue budget: same pattern as TreiberStackTest. Ensures exactly `total`
        // dequeues occur across all consumers, regardless of scheduling.
        var budget = new AtomicInteger(total);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {

            // --- Producers ---
            for (int p = 0; p < N_PRODUCERS; p++) {
                final int offset = p * N_PER_THREAD;
                exec.submit(() -> {
                    try {
                        gate.await();
                        for (int j = 0; j < N_PER_THREAD; j++) {
                            queue.enqueue(offset + j);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // --- Consumers ---
            for (int c = 0; c < N_CONSUMERS; c++) {
                exec.submit(() -> {
                    try {
                        gate.await();
                        while (budget.getAndDecrement() > 0) {
                            Integer item;
                            // Spin-yield until an item is available. A producer may not have
                            // enqueued the corresponding item yet; yield lets the virtual-thread
                            // scheduler run producers without burning a platform thread.
                            do {
                                item = queue.dequeue().orElse(null);
                                if (item == null) Thread.yield();
                            } while (item == null);
                            seen.computeIfAbsent(item, k -> new AtomicInteger()).incrementAndGet();
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

        // (a) Total distinct values seen must equal total pushed.
        assertEquals(total, seen.size(),
                "number of distinct dequeued values must equal total — missing items indicate a lost enqueue");

        // (b) Every value must have been seen exactly once.
        for (var entry : seen.entrySet()) {
            int value = entry.getKey();
            int count = entry.getValue().get();
            assertTrue(value >= 0 && value < total,
                    "dequeued value " + value + " is outside the expected range");
            assertEquals(1, count,
                    "value " + value + " was dequeued " + count + " times (expected 1)");
        }
    }
}
