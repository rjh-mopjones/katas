package org.kata.retry;

/**
 * Immutable configuration for an exponential-backoff retry strategy.
 *
 * <p>Using a record enforces immutability at the language level — there is no setter through
 * which a thread could mutate policy mid-flight — and provides free implementations of
 * {@code equals}, {@code hashCode}, and {@code toString} that are consistent with value
 * semantics. A single {@code RetryPolicy} can be safely shared across many {@link Retryer}
 * instances.
 *
 * <h2>Backoff formula</h2>
 * <pre>
 *   delayMs(attempt) = min(maxDelayMs, baseDelayMs × multiplier^(attempt - 1))
 * </pre>
 * With full jitter enabled, the actual sleep is sampled uniformly from {@code [0, delayMs]}.
 * See {@link Retryer} for the rationale behind jitter.
 *
 * <h2>Choosing parameters (interview guidance)</h2>
 * <ul>
 *   <li>{@code maxAttempts=3}, {@code baseDelayMs=100}, {@code multiplier=2.0},
 *       {@code maxDelayMs=1000} is a reasonable default for most service calls.</li>
 *   <li>Larger {@code maxAttempts} dramatically increase tail latency; prefer a
 *       deadline/budget over a fixed count in time-sensitive code paths.</li>
 *   <li>{@code maxDelayMs} prevents the theoretical cap growing unboundedly when the
 *       multiplier is large and many attempts are allowed.</li>
 * </ul>
 *
 * @param maxAttempts  total number of attempts (including the first); must be ≥ 1.
 * @param baseDelayMs  initial delay after the first failure in milliseconds; must be ≥ 0.
 * @param maxDelayMs   upper bound on any single delay; must be ≥ {@code baseDelayMs}.
 * @param multiplier   factor by which delay grows each attempt; must be ≥ 1.0.
 * @param jitter       when {@code true}, apply full jitter: actual delay = random [0, computed].
 */
public record RetryPolicy(
    int maxAttempts,
    long baseDelayMs,
    long maxDelayMs,
    double multiplier,
    boolean jitter
) {
    /** Compact canonical constructor — validates all parameters on construction. */
    public RetryPolicy {
        if (maxAttempts < 1)
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        if (baseDelayMs < 0)
            throw new IllegalArgumentException("baseDelayMs must be >= 0");
        if (maxDelayMs < baseDelayMs)
            throw new IllegalArgumentException("maxDelayMs must be >= baseDelayMs");
        if (multiplier < 1.0)
            throw new IllegalArgumentException("multiplier must be >= 1.0");
    }

    // ---- Factory helpers ----

    /**
     * Minimal policy: one attempt (no retries), no delay, no jitter.
     * Useful as a "retry-disabled" default in tests.
     */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0, 0, 1.0, false);
    }

    /**
     * Compute the delay (before optional jitter) for a given attempt number.
     *
     * @param attempt 1-based attempt index (1 = after first failure, 2 = after second, …).
     * @return capped delay in milliseconds.
     */
    public long computeDelayMs(int attempt) {
        // Math.pow is fine here — we're not in a hot loop. In very-high-frequency code one
        // could maintain a running delay variable and multiply each round, but that complicates
        // the API for negligible gain.
        double raw = baseDelayMs * Math.pow(multiplier, attempt - 1);
        // Cap at maxDelayMs to prevent unbounded growth with large multipliers.
        return Math.min(maxDelayMs, (long) raw);
    }
}
