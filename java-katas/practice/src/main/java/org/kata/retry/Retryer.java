package org.kata.retry;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;

/**
 * Executes a {@link Callable} with exponential-backoff retry, guided by a {@link RetryPolicy}.
 *
 * <h2>Why jitter matters (thundering-herd / retry storm)</h2>
 * Without jitter, all callers that hit the same transient failure retry at the same instants:
 * if 1000 threads fail at T=0, they all retry at T=base, T=base+base*multiplier, etc. This
 * creates correlated retry bursts that can turn a momentary outage into a sustained overload —
 * the "thundering herd" or "retry storm" effect. Adding randomness de-correlates retry timings
 * across callers so the downstream service sees a gradual ramp rather than a synchronized
 * spike.
 *
 * <h2>Jitter strategies (from the AWS architecture blog)</h2>
 * <ul>
 *   <li><b>Full jitter</b> — {@code sleep = random(0, computedDelay)}. Maximally spreads
 *       retry load. Can result in near-zero sleeps, so some calls retry aggressively; fine
 *       when the downstream can absorb a few fast retries.</li>
 *   <li><b>Equal jitter</b> — {@code sleep = computedDelay/2 + random(0, computedDelay/2)}.
 *       Guarantees a minimum wait of half the computed delay, limiting the very-fast-retry
 *       tail. A reasonable middle ground.</li>
 *   <li><b>Decorrelated jitter</b> — {@code sleep = random(base, lastSleep * 3)}. Grows the
 *       window based on the previous sleep, which tends to spread load even more evenly over
 *       time than full jitter for large retry counts.</li>
 * </ul>
 * This implementation provides <em>full jitter</em> as the default because it is the simplest
 * to understand and the most commonly asked-about variant in interviews.
 *
 * <h2>Idempotency requirement</h2>
 * Retries are only safe when the operation is <em>idempotent</em> or <em>at-most-once</em>
 * semantics can be achieved by the caller (e.g. via an idempotency key on the request).
 * Retrying a non-idempotent operation (e.g. charging a credit card) can cause duplicate
 * side-effects. Document this contract clearly at every call site that wraps an action in a
 * {@code Retryer}.
 *
 * <h2>What should trigger a retry?</h2>
 * Not every exception is retryable. {@code 400 Bad Request} (programmer error) should not be
 * retried; {@code 503 Service Unavailable} (transient overload) should. A production
 * {@code Retryer} would accept a {@code Predicate<Exception>} to classify exceptions. This
 * implementation retries on <em>any</em> exception for simplicity — an appropriate starting
 * point in an interview.
 */
public class Retryer {

    private final RetryPolicy policy;

    /**
     * Delay mechanism. Defaults to a real {@link Thread#sleep}; tests inject a recording
     * {@code LongConsumer} to capture the requested delays without actually sleeping.
     */
    private final LongConsumer sleeper;

    /**
     * Source of randomness for jitter. Injectable so tests can pass a {@link Random} with a
     * known seed and assert exact jitter-bounded delays deterministically.
     */
    private final Random random;

    /** Production constructor — real sleep, unseeded random. */
    public Retryer(RetryPolicy policy) {
        this(policy, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, new Random());
    }

    /**
     * @param policy  retry configuration.
     * @param sleeper a consumer that accepts a delay in milliseconds. Tests inject a recording
     *                consumer so they can assert the delay sequence without sleeping.
     * @param random  source of randomness for jitter. Pass {@code new Random(seed)} in tests
     *                for deterministic jitter values.
     */
    public Retryer(RetryPolicy policy, LongConsumer sleeper, Random random) {
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        if (sleeper == null) throw new IllegalArgumentException("sleeper must not be null");
        if (random == null) throw new IllegalArgumentException("random must not be null");
        this.policy = policy;
        this.sleeper = sleeper;
        this.random = random;
    }

    /**
     * Execute {@code action}, retrying on exception according to the configured policy.
     *
     * <p>Attempts are numbered from 1. After each failure (except the last) the retryer waits
     * for a delay computed as {@code policy.computeDelayMs(attempt)}, optionally jittered, then
     * tries again. After exhausting {@code maxAttempts}, the final exception is re-thrown as-is
     * so the caller gets the original cause without wrapping.
     *
     * @param action the operation to execute; must be idempotent — see class Javadoc.
     * @return the value returned by {@code action} on a successful attempt.
     * @throws Exception the last exception thrown by {@code action} after all attempts fail.
     */
    public <T> T execute(Callable<T> action) throws Exception {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
