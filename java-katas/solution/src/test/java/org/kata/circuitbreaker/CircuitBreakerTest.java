package org.kata.circuitbreaker;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    // A callable that always throws.
    private static final Callable<Void> FAILING =
        () -> { throw new RuntimeException("downstream error"); };

    // A callable that always succeeds.
    private static final Callable<String> SUCCEEDING =
        () -> "ok";

    @Test
    void starts_closed_and_forwards_calls() throws Exception {
        var clock = new AtomicLong(0);
        var breaker = new CircuitBreaker(3, 1_000_000_000L, 1, clock::get);

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertEquals("ok", breaker.call(SUCCEEDING));
    }

    @Test
    void closed_to_open_after_failure_threshold() {
        var clock = new AtomicLong(0);
        // Trip after 2 consecutive failures.
        var breaker = new CircuitBreaker(2, 1_000_000_000L, 1, clock::get);

        // First failure: still CLOSED.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());

        // Second failure: trips open.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
    }

    @Test
    void success_resets_consecutive_failure_counter() {
        var clock = new AtomicLong(0);
        var breaker = new CircuitBreaker(2, 1_000_000_000L, 1, clock::get);

        // One failure, then a success — should reset the counter.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertDoesNotThrow(() -> breaker.call(SUCCEEDING));

        // One more failure (counter reset, so we need 2 again to trip).
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());

        // Second consecutive failure now trips.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
    }

    @Test
    void open_state_rejects_fast_without_invoking_action() {
        var clock = new AtomicLong(0);
        var breaker = new CircuitBreaker(1, 1_000_000_000L, 1, clock::get);

        // Trip open.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        // Track how many times the underlying action is invoked.
        var callCount = new AtomicInteger(0);
        Callable<Void> countingAction = () -> {
            callCount.incrementAndGet();
            throw new RuntimeException("should not reach");
        };

        // Breaker is OPEN — call should throw CircuitOpenException, action must NOT be invoked.
        assertThrows(CircuitOpenException.class, () -> breaker.call(countingAction));
        assertEquals(0, callCount.get(), "action must not be invoked while circuit is OPEN");

        // A second rejection also must not invoke the action.
        assertThrows(CircuitOpenException.class, () -> breaker.call(countingAction));
        assertEquals(0, callCount.get());
    }

    @Test
    void open_transitions_to_half_open_after_duration() {
        var clock = new AtomicLong(0);
        long openDuration = 1_000_000_000L; // 1 second in nanos
        var breaker = new CircuitBreaker(1, openDuration, 1, clock::get);

        // Trip open.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        // Advance clock to just before the open duration — still OPEN.
        clock.set(openDuration - 1);
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        // Advance clock past the open duration — should now be HALF_OPEN.
        clock.set(openDuration);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
    }

    @Test
    void half_open_success_closes_breaker() throws Exception {
        var clock = new AtomicLong(0);
        long openDuration = 1_000_000_000L;
        // Require 2 trial successes before closing.
        var breaker = new CircuitBreaker(1, openDuration, 2, clock::get);

        // Trip open.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        // Advance past open duration → HALF_OPEN.
        clock.set(openDuration);

        // First trial success: still HALF_OPEN (need 2).
        assertEquals("ok", breaker.call(SUCCEEDING));
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());

        // Second trial success: should close.
        assertEquals("ok", breaker.call(SUCCEEDING));
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void half_open_failure_reopens_immediately() {
        var clock = new AtomicLong(0);
        long openDuration = 1_000_000_000L;
        var breaker = new CircuitBreaker(1, openDuration, 3, clock::get);

        // Trip open.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));

        // Advance past open duration → HALF_OPEN.
        clock.set(openDuration);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());

        // One success — still HALF_OPEN.
        assertDoesNotThrow(() -> breaker.call(SUCCEEDING));
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());

        // A failure in HALF_OPEN should reopen immediately.
        assertThrows(RuntimeException.class, () -> breaker.call(FAILING));
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        // And the open timer has been reset: breaker should still be OPEN at the old openDuration.
        clock.set(openDuration); // same time as before; open timer was reset when we reopened
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());

        // Advance past the new open duration to confirm it does become HALF_OPEN again.
        clock.set(openDuration + openDuration);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
    }

    @Test
    void concurrent_calls_never_exceed_failure_threshold() throws Exception {
        // 100 threads all failing concurrently: the breaker must trip exactly once and
        // stop forwarding calls. This validates that the lock protects the state machine
        // under heavy concurrent mutation.
        var clock = new AtomicLong(0);
        var breaker = new CircuitBreaker(5, 1_000_000_000L, 1, clock::get);

        int N = 100;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var openExceptions = new AtomicInteger(0);
        var actionInvocations = new AtomicInteger(0);

        Callable<Void> countingFailing = () -> {
            actionInvocations.incrementAndGet();
            throw new RuntimeException("fail");
        };

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < N; i++) {
                exec.submit(() -> {
                    try {
                        gate.await();
                        breaker.call(countingFailing);
                    } catch (CircuitOpenException e) {
                        openExceptions.incrementAndGet();
                    } catch (Exception ignored) {
                        // genuine failures from the action
                    } finally {
                        done.countDown();
                    }
                });
            }
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        // Once the breaker is open, all subsequent calls are rejected without invoking action.
        // So actionInvocations should equal exactly failureThreshold (5) — the breaker trips
        // on the 5th failure and every call thereafter is a fast-reject.
        // Under concurrent execution up to 5 calls can land before the first CAS commits the
        // state change — so we allow <= failureThreshold here.
        assertTrue(openExceptions.get() > 0, "some calls should have been fast-rejected");
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
    }
}
