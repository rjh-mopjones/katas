package org.kata.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LeakyBucketRateLimiterTest {

    @Test
    void initial_bucket_is_empty_so_first_requests_are_admitted() {
        // New buckets start empty (level=0), so requests up to capacity are immediately admitted.
        var clock = new AtomicLong(0);
        var limiter = new LeakyBucketRateLimiter(5, 1.0, clock::get);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("key"), "request " + i + " should be admitted");
    }

    @Test
    void bucket_rejects_when_full() {
        var clock = new AtomicLong(0);
        var limiter = new LeakyBucketRateLimiter(5, 1.0, clock::get);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key");   // fill to capacity
        assertFalse(limiter.tryAcquire("key"), "bucket full — should be rejected");
    }

    @Test
    void bucket_admits_again_after_enough_leak_time() {
        // Capacity=5, leak=1/sec. Fill the bucket then advance 1 second.
        var clock = new AtomicLong(0);
        var limiter = new LeakyBucketRateLimiter(5, 1.0, clock::get);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key");
        assertFalse(limiter.tryAcquire("key"), "bucket full");

        // Advance 1 second — 1 unit leaks out, freeing room for 1 request.
        clock.addAndGet(1_000_000_000L);
        assertTrue(limiter.tryAcquire("key"), "one unit leaked — should be admitted");
        assertFalse(limiter.tryAcquire("key"), "bucket full again after one admission");
    }

    @Test
    void partial_drain_reflects_fractional_leak() {
        // Capacity=10, leak=10/sec (1 per 100ms). Fill 8, advance 500ms — 5 units drain out,
        // leaving level=3, so 7 more can be admitted.
        var clock = new AtomicLong(0);
        var limiter = new LeakyBucketRateLimiter(10, 10.0, clock::get);
        for (int i = 0; i < 8; i++) assertTrue(limiter.tryAcquire("key"));

        // Drain 5 units (500ms × 10/sec).
        clock.addAndGet(500_000_000L);
        // Level is now 3; 7 more fit.
        for (int i = 0; i < 7; i++) assertTrue(limiter.tryAcquire("key"), "slot " + i + " should fit");
        // One more would take level to 11 > 10 — rejected.
        assertFalse(limiter.tryAcquire("key"), "bucket full after 7 more admits");
    }

    @Test
    void per_key_buckets_are_independent() {
        var clock = new AtomicLong(0);
        var limiter = new LeakyBucketRateLimiter(2, 1.0, clock::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"), "bucket 'a' is full");
        // Bucket for 'b' is untouched — both slots available.
        assertTrue(limiter.tryAcquire("b"));
        assertTrue(limiter.tryAcquire("b"));
        assertFalse(limiter.tryAcquire("b"), "bucket 'b' is full");
    }

    @Test
    void request_larger_than_capacity_is_always_rejected() {
        var clock = new AtomicLong(0);
        // Bucket is empty — but asking for n > capacity is unsatisfiable.
        var limiter = new LeakyBucketRateLimiter(5, 1.0, clock::get);
        assertFalse(limiter.tryAcquire("key", 6));
    }

    @Test
    void extended_idle_period_empties_bucket_completely() {
        // After enough time all water drains; bucket returns to level=0.
        var clock = new AtomicLong(0);
        var limiter = new LeakyBucketRateLimiter(5, 1.0, clock::get);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key");
        // Advance 100 seconds — bucket fully drained (5 units × 1/sec = 5s, but we clamp at 0).
        clock.addAndGet(100_000_000_000L);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("key"), "bucket should be empty");
        assertFalse(limiter.tryAcquire("key"));
    }

    @Test
    void concurrent_acquires_never_exceed_capacity_under_frozen_clock() throws Exception {
        // Frozen clock means zero drain during the test. Capacity=50, 200 threads race to
        // acquire. Exactly 50 should win — the CAS loop must enforce the limit with no leaks.
        var clock = new AtomicLong(0);
        var limiter = new LeakyBucketRateLimiter(50, 1.0, clock::get);

        int N = 200;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var wins = new AtomicInteger();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    if (limiter.tryAcquire("k")) wins.incrementAndGet();
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "test timed out — possible deadlock");
        }

        // Exactly capacity many threads should have won; the rest must have been rejected.
        assertEquals(50, wins.get(), "concurrent admits must not exceed bucket capacity");
    }
}
