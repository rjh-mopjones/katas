package org.kata.circuitbreaker;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * A thread-safe circuit breaker that protects a downstream dependency from overload and gives it
 * time to recover.
 *
 * <h2>State machine</h2>
 * <pre>
 *            N consecutive failures
 *  CLOSED ─────────────────────────────▶ OPEN
 *    ▲                                     │
 *    │                                     │ open-duration elapses
 *    │                                     ▼
 *    │        successThreshold successes  HALF_OPEN
 *    └─────────────────────────────────────┤
 *                                          │ any failure
 *                                          └──────────────────▶ OPEN (resets timer)
 * </pre>
 *
 * <ul>
 *   <li><b>CLOSED</b> — normal operation. Every call is forwarded to the action. On failure the
 *       consecutive-failure counter increments; on success it resets to zero. When the counter
 *       reaches {@code failureThreshold} the breaker trips to OPEN.</li>
 *   <li><b>OPEN</b> — fast-rejection. {@link #call} throws {@link CircuitOpenException}
 *       immediately, without invoking the action. This sheds load from the failing service and
 *       gives it headroom to recover. After {@code openDurationNanos} the breaker moves to
 *       HALF_OPEN to probe whether the service is healthy again.</li>
 *   <li><b>HALF_OPEN</b> — single trial. A limited number of calls ({@code successThreshold})
 *       are allowed through. Each success increments a trial counter; when it reaches
 *       {@code successThreshold} the breaker closes. The first failure in this state immediately
 *       reopens the breaker and resets the open timer.</li>
 * </ul>
 *
 * <h2>Consecutive failures vs rolling failure-rate window</h2>
 * This implementation counts <em>consecutive</em> failures: any single success resets the
 * counter. The alternative — a rolling failure-rate window (e.g., "≥50% in last 10 calls") —
 * is more nuanced: it tolerates transient blips and is less prone to opening on a short but
 * non-representative burst. Resilience4j defaults to a count-based or time-based <em>sliding
 * window</em> for exactly this reason. Consecutive count is simpler to reason about and is the
 * right starting point in an interview.
 *
 * <h2>Thread-safety: lock vs CAS</h2>
 * State transitions involve reading <em>and</em> conditionally writing up to three fields
 * (state, failure counter, open-since timestamp) as a single unit. Modelling that as a single
 * immutable record in an {@link java.util.concurrent.atomic.AtomicReference} is possible but
 * makes the half-open trial counter awkward (it would need to be part of the same record while
 * the state is HALF_OPEN). A {@link ReentrantLock} is chosen here:
 * <ul>
 *   <li>State transitions are infrequent; the lock is held for microseconds.</li>
 *   <li>The happy-path (CLOSED, action succeeds) still contends briefly, but in real usage the
 *       action itself (network call, DB query) takes orders of magnitude longer than the lock
 *       overhead — so the lock is not the bottleneck.</li>
 *   <li>The code is significantly easier to read and review than a CAS-based state machine with
 *       multiple fields.</li>
 * </ul>
 * If the action is extremely cheap and the breaker is on a very hot path, one could move to a
 * single {@link java.util.concurrent.atomic.AtomicReference} on an immutable state record and
 * accept the CAS complexity.
 *
 * <h2>Named alternatives</h2>
 * <ul>
 *   <li><b>Resilience4j</b> — production-quality, supports count/time-based sliding windows,
 *       metrics, events, and composition with retry/bulkhead/rate-limiter decorators.</li>
 *   <li><b>Hystrix</b> (Netflix, now in maintenance mode) — introduced the pattern to the
 *       Java ecosystem; uses thread pools per dependency for bulkhead isolation.</li>
 *   <li><b>Sentinel</b> (Alibaba) — flow control, circuit breaking, and system-adaptive
 *       protection in a single library.</li>
 * </ul>
 */
public class CircuitBreaker {

    /**
     * The three observable states of the breaker. Named as a sealed set so a {@code switch}
     * expression over {@code State} is exhaustive without a default arm.
     */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    // ---- Configuration (immutable after construction) ----

    /** Number of consecutive failures required to trip from CLOSED to OPEN. */
    private final int failureThreshold;

    /**
     * How long the breaker stays OPEN before admitting a single trial, in nanoseconds.
     * Stored in nanos to match the injectable clock unit (nanoTime).
     */
    private final long openDurationNanos;

    /**
     * Number of consecutive successes in HALF_OPEN needed to close the breaker.
     * Setting this to 1 is the minimal "one success = healthy" check; higher values provide
     * greater confidence that the service has truly recovered before resuming full traffic.
     */
    private final int successThreshold;

    /** Monotonic clock. Never {@link System#currentTimeMillis()} — see constructor javadoc. */
    private final LongSupplier clock;

    // ---- Mutable state (all guarded by `lock`) ----

    private final ReentrantLock lock = new ReentrantLock();

    private State state = State.CLOSED;

    /** Consecutive failure count; resets to zero on any success in CLOSED state. */
    private int consecutiveFailures = 0;

    /** Trial success count while in HALF_OPEN; resets to zero when entering OPEN. */
    private int trialSuccesses = 0;

    /**
     * The nanoTime at which the breaker last transitioned to OPEN. Used to determine when the
     * open duration has elapsed and the breaker should advance to HALF_OPEN.
     */
    private long openedAtNanos = 0;

    // ---- Constructors ----

    /**
     * Convenience constructor using the real monotonic clock.
     *
     * @param failureThreshold   consecutive failures to trip open; must be ≥ 1.
     * @param openDurationNanos  nanoseconds to stay open before allowing trial calls; must be > 0.
     * @param successThreshold   HALF_OPEN successes needed to close; must be ≥ 1.
     */
    public CircuitBreaker(int failureThreshold, long openDurationNanos, int successThreshold) {
        this(failureThreshold, openDurationNanos, successThreshold, System::nanoTime);
    }

    /**
     * @param clock injectable monotonic clock. Tests pass an {@link java.util.concurrent.atomic.AtomicLong}
     *              to advance time without sleeping. Production uses {@link System#nanoTime()}.
     *              <p><b>Why not {@link System#currentTimeMillis()}?</b> Wall-clock time can
     *              jump backwards (NTP adjustments, daylight-saving, leap-second handling). A
     *              backwards step would make {@code elapsed} negative, which would either
     *              incorrectly keep the breaker open forever or, with a naive {@code Math.max(0,…)},
     *              silently reset the open window. {@code nanoTime} is guaranteed monotonic
     *              within a JVM process and is the correct choice for measuring elapsed durations.
     */
    public CircuitBreaker(int failureThreshold, long openDurationNanos, int successThreshold,
                          LongSupplier clock) {
        if (failureThreshold < 1)
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        if (openDurationNanos <= 0)
            throw new IllegalArgumentException("openDurationNanos must be positive");
        if (successThreshold < 1)
            throw new IllegalArgumentException("successThreshold must be >= 1");
        this.failureThreshold = failureThreshold;
        this.openDurationNanos = openDurationNanos;
        this.successThreshold = successThreshold;
        this.clock = clock;
    }

    // ---- Public API ----

    /**
     * Returns the current state of the breaker.
     *
     * <p>Note: the returned value is a snapshot — the state may change immediately after this
     * call returns. Use it for monitoring/metrics, not for conditional logic.
     */
    public State state() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Execute {@code action} through the circuit breaker.
     *
     * <p>When OPEN, throws {@link CircuitOpenException} immediately — {@code action} is never
     * called. When CLOSED or HALF_OPEN, {@code action} is invoked; the outcome drives the state
     * machine as described in the class Javadoc.
     *
     * @param action the operation to protect; may throw any exception.
     * @return the value returned by {@code action}.
     * @throws CircuitOpenException if the breaker is OPEN.
     * @throws Exception            any exception propagated from {@code action} itself.
     */
    public <T> T call(Callable<T> action) throws Exception {
        throw new UnsupportedOperationException("TODO: implement");
    }

    // ---- Internal state-machine transitions ----

    /**
     * If the breaker is OPEN and the open duration has elapsed, advance to HALF_OPEN.
     * Must be called under {@code lock}.
     */
    private void maybeTransitionToHalfOpen() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Record a successful call outcome. Transitions HALF_OPEN → CLOSED when enough trial
     * successes have accumulated; resets the failure counter in CLOSED.
     *
     * @param stateAtCallTime the state observed when the call was dispatched. We use this rather
     *                        than re-reading {@code state} under the lock so that a race where
     *                        another thread trips the breaker open mid-flight doesn't confuse
     *                        HALF_OPEN trial counting.
     */
    private void recordSuccess(State stateAtCallTime) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Record a failed call outcome. Transitions CLOSED → OPEN on threshold breach, and
     * HALF_OPEN → OPEN immediately (any failure in trial mode reopens the breaker).
     */
    private void recordFailure(State stateAtCallTime) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Transition unconditionally to OPEN and stamp the open time.
     * Must be called under {@code lock}.
     */
    private void tripOpen() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
