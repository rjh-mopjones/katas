package org.kata.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    @Test
    void initial_bucket_starts_full() {
        var clock = new AtomicLong(0);
        var limiter = new TokenBucketRateLimiter(5, 1.0, clock::get);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));   // 6th rejected
    }

    @Test
    void tokens_refill_at_rate() {
        var clock = new AtomicLong(0);
        var limiter = new TokenBucketRateLimiter(5, 1.0, clock::get);   // 1 token/sec
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key");           // drain
        assertFalse(limiter.tryAcquire("key"));

        clock.addAndGet(1_000_000_000L);   // advance 1 sec
        assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));   // only 1 refilled
    }

    @Test
    void refill_is_capped_at_capacity() {
        var clock = new AtomicLong(0);
        var limiter = new TokenBucketRateLimiter(5, 1.0, clock::get);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("key");

        clock.addAndGet(100_000_000_000L);   // advance 100s — would refill 100 tokens
        // Only capacity (5) should be available
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("key"));
        assertFalse(limiter.tryAcquire("key"));
    }

    @Test
    void per_key_buckets_are_independent() {
        var clock = new AtomicLong(0);
        var limiter = new TokenBucketRateLimiter(2, 1.0, clock::get);
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));    // independent bucket
    }

    @Test
    void concurrent_acquires_never_exceed_capacity() throws Exception {
        // Frozen clock — no refill during test. Capacity 50, 200 threads — exactly 50 win.
        var clock = new AtomicLong(0);
        var limiter = new TokenBucketRateLimiter(50, 1.0, clock::get);

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
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(50, wins.get());
    }
}
