package org.kata.connectionpool;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolTest {

    /** Simple resource type for testing: wraps an integer id. */
    private record FakeResource(int id) {}

    @Test
    void borrow_up_to_max_succeeds_immediately() throws Exception {
        var pool = new ConnectionPool<>(() -> new FakeResource(0), 3);

        var r1 = pool.borrow(1, TimeUnit.SECONDS);
        var r2 = pool.borrow(1, TimeUnit.SECONDS);
        var r3 = pool.borrow(1, TimeUnit.SECONDS);

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);
        assertEquals(3, pool.inUse());
        assertEquals(0, pool.available());
    }

    @Test
    void borrowing_beyond_max_times_out() throws Exception {
        var pool = new ConnectionPool<>(() -> new FakeResource(0), 2);

        var r1 = pool.borrow(1, TimeUnit.SECONDS);
        var r2 = pool.borrow(1, TimeUnit.SECONDS);
        assertNotNull(r1);
        assertNotNull(r2);

        // No permits left — this borrow should time out quickly.
        var r3 = pool.borrow(50, TimeUnit.MILLISECONDS);
        assertNull(r3, "borrow should return null when pool is exhausted and timeout elapses");
    }

    @Test
    void release_makes_resource_borrowable_again() throws Exception {
        var pool = new ConnectionPool<>(() -> new FakeResource(0), 1);

        var r1 = pool.borrow(1, TimeUnit.SECONDS);
        assertNotNull(r1);

        // Pool exhausted.
        assertNull(pool.borrow(20, TimeUnit.MILLISECONDS));

        // Release and borrow again.
        pool.release(r1);
        var r2 = pool.borrow(1, TimeUnit.SECONDS);
        assertNotNull(r2, "resource should be borrowable again after release");
        assertEquals(0, pool.available());
        assertEquals(1, pool.inUse());

        pool.release(r2);
    }

    @Test
    void released_resource_is_reused_not_recreated() throws Exception {
        // Factory increments a counter; we assert the total created count stays bounded.
        var createCount = new AtomicInteger(0);
        var pool = new ConnectionPool<>(() -> new FakeResource(createCount.incrementAndGet()), 2);

        var r1 = pool.borrow(1, TimeUnit.SECONDS);
        pool.release(r1);
        var r2 = pool.borrow(1, TimeUnit.SECONDS);

        // Only one resource should have been created (it was reused).
        assertEquals(1, createCount.get(), "resource should be reused, not recreated");
        pool.release(r2);
    }

    @Test
    void invalid_resources_are_discarded_and_factory_called_again() throws Exception {
        var createCount = new AtomicInteger(0);
        // Validator: resource id == 1 is "stale"; all others are valid.
        var pool = new ConnectionPool<>(
            () -> new FakeResource(createCount.incrementAndGet()),
            3,
            r -> r.id() != 1 // id 1 is always invalid
        );

        // Borrow and release resource id=1 (it goes to idle queue with id=1).
        var r1 = pool.borrow(1, TimeUnit.SECONDS);
        assertEquals(1, r1.id());
        pool.release(r1);

        // Next borrow: pool polls id=1, validator rejects it, factory creates id=2.
        var r2 = pool.borrow(1, TimeUnit.SECONDS);
        assertNotNull(r2);
        assertNotEquals(1, r2.id(), "invalid resource (id=1) must not be handed out");
        assertEquals(2, createCount.get(), "factory should have been called twice (1 discarded, 1 created)");
        pool.release(r2);
    }

    @Test
    void in_use_and_available_counts_are_consistent() throws Exception {
        var pool = new ConnectionPool<>(() -> new FakeResource(0), 3);

        assertEquals(0, pool.inUse());

        var r1 = pool.borrow(1, TimeUnit.SECONDS);
        assertEquals(1, pool.inUse());

        var r2 = pool.borrow(1, TimeUnit.SECONDS);
        assertEquals(2, pool.inUse());

        pool.release(r1);
        assertEquals(1, pool.inUse());
        assertEquals(1, pool.available());

        pool.release(r2);
        assertEquals(0, pool.inUse());
        assertEquals(2, pool.available());
    }

    @Test
    void concurrent_borrows_never_exceed_max_in_use() throws Exception {
        final int maxSize = 3;
        var pool = new ConnectionPool<>(() -> new FakeResource(0), maxSize);

        final int N = 30; // threads
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var maxObservedInUse = new AtomicInteger(0);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < N; i++) {
                exec.submit(() -> {
                    try {
                        gate.await();
                        FakeResource r = pool.borrow(2, TimeUnit.SECONDS);
                        if (r != null) {
                            // Snapshot inUse while holding the resource.
                            maxObservedInUse.accumulateAndGet(pool.inUse(), Math::max);
                            // Simulate a small amount of work so concurrent overlap happens.
                            Thread.sleep(5);
                            pool.release(r);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            gate.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "threads timed out");
        }

        assertTrue(maxObservedInUse.get() <= maxSize,
            "inUse " + maxObservedInUse.get() + " exceeded maxSize " + maxSize);
    }

    @Test
    void blocking_borrow_unblocks_when_resource_released() throws Exception {
        var pool = new ConnectionPool<>(() -> new FakeResource(0), 1);

        // Borrow the only resource.
        var r1 = pool.borrow(1, TimeUnit.SECONDS);
        assertNotNull(r1);

        var waiterFinished = new CountDownLatch(1);
        var waiterStarted = new CountDownLatch(1);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            exec.submit(() -> {
                try {
                    waiterStarted.countDown();
                    FakeResource r2 = pool.borrow(5, TimeUnit.SECONDS);
                    assertNotNull(r2);
                    pool.release(r2);
                    waiterFinished.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(waiterStarted.await(2, TimeUnit.SECONDS));
            // Give the waiter thread time to block on the semaphore.
            assertFalse(waiterFinished.await(50, TimeUnit.MILLISECONDS),
                "waiter should still be blocking");

            // Release r1 — the waiter should now proceed.
            pool.release(r1);
            assertTrue(waiterFinished.await(2, TimeUnit.SECONDS),
                "waiter should have been unblocked by the release");
        }
    }

    @Test
    void null_factory_and_invalid_maxSize_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConnectionPool<>(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new ConnectionPool<>(() -> "", 0));
    }
}
