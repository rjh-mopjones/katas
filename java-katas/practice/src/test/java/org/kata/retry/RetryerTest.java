package org.kata.retry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryerTest {

    /** Sleeper that records every requested delay instead of sleeping. */
    private static List<Long> recordingSleeperDelays() {
        return new ArrayList<>();
    }

    @Test
    void succeeds_on_first_attempt_without_any_retry() throws Exception {
        var policy = new RetryPolicy(3, 100, 1000, 2.0, false);
        var delays = new ArrayList<Long>();
        var retryer = new Retryer(policy, delays::add, new Random(0));

        var result = retryer.execute(() -> "hello");

        assertEquals("hello", result);
        assertTrue(delays.isEmpty(), "no delay should be recorded when first attempt succeeds");
    }

    @Test
    void succeeds_on_third_attempt_after_two_failures() throws Exception {
        var policy = new RetryPolicy(3, 100, 1000, 2.0, false);
        var delays = new ArrayList<Long>();
        var retryer = new Retryer(policy, delays::add, new Random(0));

        var callCount = new AtomicInteger(0);
        var result = retryer.execute(() -> {
            int call = callCount.incrementAndGet();
            if (call < 3) throw new RuntimeException("attempt " + call + " failed");
            return "success";
        });

        assertEquals("success", result);
        assertEquals(3, callCount.get());
        // Two failures → two delay records.
        assertEquals(2, delays.size());
    }

    @Test
    void exhausts_attempts_and_rethrows_last_exception() {
        var policy = new RetryPolicy(3, 100, 1000, 2.0, false);
        var delays = new ArrayList<Long>();
        var retryer = new Retryer(policy, delays::add, new Random(0));

        var callCount = new AtomicInteger(0);
        var thrown = assertThrows(RuntimeException.class, () ->
            retryer.execute(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("always fails");
            })
        );

        assertEquals("always fails", thrown.getMessage());
        assertEquals(3, callCount.get(), "action should be called maxAttempts times");
        // After the last (3rd) attempt we do not sleep — only 2 sleeps for 3 attempts.
        assertEquals(2, delays.size(), "should sleep between each pair of attempts");
    }

    @Test
    void backoff_delays_grow_exponentially_and_are_capped() {
        // base=100, multiplier=3, maxDelay=500, jitter=false
        // attempt 1 failure: delay = min(500, 100 * 3^0) = 100
        // attempt 2 failure: delay = min(500, 100 * 3^1) = 300
        // attempt 3 failure: delay = min(500, 100 * 3^2) = 500  ← capped
        // attempt 4 failure: delay = min(500, 100 * 3^3) = 500  ← capped
        var policy = new RetryPolicy(5, 100, 500, 3.0, false);
        var delays = new ArrayList<Long>();
        var retryer = new Retryer(policy, delays::add, new Random(0));

        assertThrows(RuntimeException.class, () ->
            retryer.execute(() -> { throw new RuntimeException("always fails"); })
        );

        assertEquals(4, delays.size()); // 5 attempts → 4 inter-attempt gaps
        assertEquals(100L, delays.get(0));
        assertEquals(300L, delays.get(1));
        assertEquals(500L, delays.get(2), "third delay should be capped at maxDelayMs");
        assertEquals(500L, delays.get(3), "fourth delay should also be capped");
    }

    @Test
    void jitter_bounds_delay_within_zero_and_computed() {
        // With full jitter, every recorded delay must be in [0, computedDelay].
        // Use a seeded Random for determinism.
        var policy = new RetryPolicy(4, 200, 800, 2.0, true);
        var delays = new ArrayList<Long>();
        var seededRandom = new Random(42L); // fixed seed → deterministic
        var retryer = new Retryer(policy, delays::add, seededRandom);

        assertThrows(RuntimeException.class, () ->
            retryer.execute(() -> { throw new RuntimeException("always fails"); })
        );

        assertEquals(3, delays.size());
        // Computed (un-jittered) delays: 200, 400, 800
        long[] computed = {200L, 400L, 800L};
        for (int i = 0; i < delays.size(); i++) {
            long d = delays.get(i);
            assertTrue(d >= 0, "jittered delay must be non-negative, was " + d);
            assertTrue(d <= computed[i],
                "jittered delay " + d + " must not exceed computed " + computed[i]);
        }
    }

    @Test
    void no_retry_policy_propagates_exception_immediately() {
        var policy = RetryPolicy.noRetry();
        var delays = new ArrayList<Long>();
        var retryer = new Retryer(policy, delays::add, new Random(0));

        var callCount = new AtomicInteger(0);
        assertThrows(RuntimeException.class, () ->
            retryer.execute(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("fail");
            })
        );

        assertEquals(1, callCount.get(), "only one attempt with noRetry policy");
        assertTrue(delays.isEmpty(), "no delay for single-attempt policy");
    }

    @Test
    void record_validates_illegal_arguments() {
        // Verify RetryPolicy validates its invariants on construction.
        assertThrows(IllegalArgumentException.class,
            () -> new RetryPolicy(0, 100, 1000, 2.0, false),
            "maxAttempts < 1 should throw");
        assertThrows(IllegalArgumentException.class,
            () -> new RetryPolicy(3, -1, 1000, 2.0, false),
            "negative baseDelayMs should throw");
        assertThrows(IllegalArgumentException.class,
            () -> new RetryPolicy(3, 200, 100, 2.0, false),
            "maxDelay < baseDelay should throw");
        assertThrows(IllegalArgumentException.class,
            () -> new RetryPolicy(3, 100, 1000, 0.5, false),
            "multiplier < 1 should throw");
    }
}
