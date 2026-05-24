package org.kata.blockingqueue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BoundedBlockingQueueTest {

    @Test
    void basic_put_and_take_fifo_order() throws InterruptedException {
        var queue = new BoundedBlockingQueue<Integer>(3);
        queue.put(1);
        queue.put(2);
        queue.put(3);
        assertEquals(1, queue.take());
        assertEquals(2, queue.take());
        assertEquals(3, queue.take());
    }

    @Test
    void size_reflects_current_element_count() throws InterruptedException {
        var queue = new BoundedBlockingQueue<String>(5);
        assertEquals(0, queue.size());
        queue.put("a");
        assertEquals(1, queue.size());
        queue.put("b");
        assertEquals(2, queue.size());
        queue.take();
        assertEquals(1, queue.size());
    }

    @Test
    void put_blocks_when_full_until_take_frees_space() throws Exception {
        var queue = new BoundedBlockingQueue<Integer>(1);
        queue.put(42); // fill the queue

        // A second put should block. We run it on a separate virtual thread and check
        // that it completes only after we call take().
        var putterStarted = new CountDownLatch(1);
        var putterFinished = new CountDownLatch(1);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            exec.submit(() -> {
                try {
                    putterStarted.countDown();
                    queue.put(99);
                    putterFinished.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(putterStarted.await(2, TimeUnit.SECONDS));

            // Give the putter thread a moment to actually block on the condition.
            // (We cannot guarantee it has reached await() yet, but 50 ms is generous.)
            assertFalse(putterFinished.await(50, TimeUnit.MILLISECONDS),
                "putter should still be blocked while queue is full");

            // Taking an element frees a slot; the putter should now unblock.
            assertEquals(42, queue.take());
            assertTrue(putterFinished.await(2, TimeUnit.SECONDS),
                "putter should have unblocked after take");
            assertEquals(99, queue.take());
        }
    }

    @Test
    void take_blocks_when_empty_until_put_arrives() throws Exception {
        var queue = new BoundedBlockingQueue<String>(5);

        var takerStarted = new CountDownLatch(1);
        var takerResult = new CountDownLatch(1);
        var holder = new String[1]; // single-element array to capture result from lambda

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            exec.submit(() -> {
                try {
                    takerStarted.countDown();
                    holder[0] = queue.take(); // blocks until an item is available
                    takerResult.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(takerStarted.await(2, TimeUnit.SECONDS));

            // Queue is empty — taker must still be blocking.
            assertFalse(takerResult.await(50, TimeUnit.MILLISECONDS),
                "taker should be blocked while queue is empty");

            // A put should unblock the taker.
            queue.put("hello");
            assertTrue(takerResult.await(2, TimeUnit.SECONDS),
                "taker should unblock after put");
            assertEquals("hello", holder[0]);
        }
    }

    @Test
    void concurrent_producers_consumers_all_items_consumed_exactly_once() throws Exception {
        // N producers each put M items; N consumers take items until all are consumed.
        // Assert that every single item is consumed exactly once (no duplicates, no losses).
        final int N = 4;   // producer and consumer thread count
        final int M = 100; // items per producer
        final int total = N * M;

        var queue = new BoundedBlockingQueue<Integer>(10); // capacity < total to force blocking
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N + N); // N producers + N consumers
        // Track how many times each value is seen. Values are 0..total-1 (producer i puts i*M+j).
        var seen = new ConcurrentHashMap<Integer, AtomicInteger>();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            // Launch producers.
            for (int i = 0; i < N; i++) {
                final int producerId = i;
                exec.submit(() -> {
                    try {
                        gate.await();
                        for (int j = 0; j < M; j++) {
                            queue.put(producerId * M + j);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // Launch consumers.
            var remaining = new AtomicInteger(total);
            for (int i = 0; i < N; i++) {
                exec.submit(() -> {
                    try {
                        gate.await();
                        while (remaining.decrementAndGet() >= 0) {
                            Integer item = queue.take();
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
            assertTrue(done.await(10, TimeUnit.SECONDS), "producer/consumer threads timed out");
        }

        // Every item in [0, total) should have been consumed exactly once.
        assertEquals(total, seen.size(), "number of distinct items seen should equal total");
        for (var entry : seen.entrySet()) {
            assertEquals(1, entry.getValue().get(),
                "item " + entry.getKey() + " was consumed " + entry.getValue().get() + " times");
        }
    }

    @Test
    void queue_never_exceeds_capacity_under_concurrent_load() throws Exception {
        final int capacity = 5;
        var queue = new BoundedBlockingQueue<Integer>(capacity);
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(50);
        var maxObservedSize = new AtomicInteger(0);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            // 25 producers, each trying to put one item.
            for (int i = 0; i < 25; i++) {
                final int val = i;
                exec.submit(() -> {
                    try {
                        gate.await();
                        queue.put(val);
                        // Sample size under a separate lock to avoid holding queue's internal
                        // lock, which is not exposed. A CAS on maxObservedSize suffices.
                        int s = queue.size();
                        // Update max observed size atomically.
                        maxObservedSize.accumulateAndGet(s, Math::max);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            // 25 consumers, each taking one item.
            for (int i = 0; i < 25; i++) {
                exec.submit(() -> {
                    try {
                        gate.await();
                        queue.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            gate.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        }

        assertTrue(maxObservedSize.get() <= capacity,
            "queue size " + maxObservedSize.get() + " exceeded capacity " + capacity);
    }

    @Test
    void null_element_rejected() {
        var queue = new BoundedBlockingQueue<String>(5);
        assertThrows(NullPointerException.class, () -> queue.put(null));
    }

    @Test
    void capacity_below_one_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedBlockingQueue<>(0));
    }
}
