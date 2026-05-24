package org.kata.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowRateLimiterTest {

    @Test
    void requests_within_limit_are_admitted() {
        var clock = new AtomicLong(0);
        // Limit 5 per 1-second window.
        var limiter = new SlidingWindowRateLimiter(5, 1_000_000_000L, clock::get);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("key"), "request " + i + " should be admitted");
    }

    @Test
    void requests_beyond_limit_are_rejected() {
        var clock = new AtomicLong(0);
        var limiter = new SlidingWindowRateLimiter(5, 1_000_000_000L, clock::get);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key");   // consume limit
        assertFalse(limiter.tryAcquire("key"), "6th request in window must be rejected");
    }

    @Test
    void limit_resets_after_full_window_elapses() {
        // Advance past the window boundary — a fresh window with zero count starts.
        var clock = new AtomicLong(0);
        var limiter = new SlidingWindowRateLimiter(5, 1_000_000_000L, clock::get);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key");
        assertFalse(limiter.tryAcquire("key"));

        // Advance 1 full window + a tiny bit so we enter the next fixed window.
        // prevCount = 5, currCount = 0, elapsed-in-new-window ≈ 0 → prevWeight ≈ 1.0
        // estimate = 5 × 1.0 + 0 = 5 → still at limit.  Need to advance further.
        // Advance 2 full windows — prev becomes empty, so estimate = 0.
        clock.addAndGet(2_000_000_000L);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("key"), "window refreshed — should be admitted");
        assertFalse(limiter.tryAcquire("key"));
    }

    @Test
    void sliding_window_prevents_boundary_spike() {
        // Fixed-window bug: send limit requests at end of window T0, then limit at start of T1.
        // The sliding window counter should see both sets and block the second set.
        var clock = new AtomicLong(0);
        long windowNanos = 1_000_000_000L;
        var limiter = new SlidingWindowRateLimiter(5, windowNanos, clock::get);

        // Send 5 requests at time = 0 (start of first window).
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("key"));

        // Advance to just into the second window (500ms in), so prevWeight ≈ 0.5.
        // Estimate = 5 × 0.5 + 0 = 2.5 → 2 more fit (5 - 2.5 ≈ 2).
        clock.addAndGet(windowNanos + windowNanos / 2);  // 1.5 seconds total

        // Some requests should still be blocked because the previous window's count weighs in.
        // At 50% through window 2: estimate = 5 × 0.5 = 2.5. Limit 5 → budget ≈ 2.
        int admitted = 0;
        for (int i = 0; i < 5; i++) {
            if (limiter.tryAcquire("key")) admitted++;
        }
        // Should be fewer than 5 because the previous window's traffic still counts.
        assertTrue(admitted < 5, "sliding window must throttle boundary spike; admitted=" + admitted);
    }

    @Test
    void partial_window_overlap_proportionally_weights_previous_count() {
        // At the 25% mark of window 2, prevWeight = 0.75.
        // prev=4 requests, curr=0 → estimate = 3.0 → budget = 5 - 3.0 = 2.
        var clock = new AtomicLong(0);
        long windowNanos = 1_000_000_000L;
        var limiter = new SlidingWindowRateLimiter(5, windowNanos, clock::get);

        for (int i = 0; i < 4; i++) assertTrue(limiter.tryAcquire("key"));  // prev=4

        // Advance exactly 1.25 windows: prev=4, currCount=0, fractionOfCurrent=0.25,
        // prevWeight=0.75 → estimate=3.0 → budget=2.
        clock.addAndGet(windowNanos + windowNanos / 4);

        assertTrue(limiter.tryAcquire("key"),  "first request fits (estimate 3.0 → budget 2)");
        assertTrue(limiter.tryAcquire("key"),  "second request fits (estimate 3.0+1=4.0 → budget 1)");
        assertFalse(limiter.tryAcquire("key"), "third request blocked (estimate 4.0+1=5.0 > 5)");
    }

    @Test
    void per_key_windows_are_independent() {
        var clock = new AtomicLong(0);
        var limiter = new SlidingWindowRateLimiter(2, 1_000_000_000L, clock::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"), "key 'a' exhausted");
        // Key 'b' has its own window — untouched.
        assertTrue(limiter.tryAcquire("b"));
        assertTrue(limiter.tryAcquire("b"));
        assertFalse(limiter.tryAcquire("b"), "key 'b' exhausted");
    }

    @Test
    void request_larger_than_limit_always_rejected() {
        var clock = new AtomicLong(0);
        var limiter = new SlidingWindowRateLimiter(5, 1_000_000_000L, clock::get);
        assertFalse(limiter.tryAcquire("key", 6), "n > limit is always unsatisfiable");
    }

    @Test
    void concurrent_acquires_never_exceed_limit_under_frozen_clock() throws Exception {
        // Frozen clock keeps all requests in the same window. Limit=50, 200 threads race.
        // Exactly 50 should win — CAS must enforce the limit without letting threads race past it.
        var clock = new AtomicLong(0);
        var limiter = new SlidingWindowRateLimiter(50, 1_000_000_000L, clock::get);

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

        assertEquals(50, wins.get(), "concurrent admits must not exceed window limit");
    }
}
