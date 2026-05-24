package org.kata.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Lock-free, per-key leaky bucket rate limiter.
 *
 * <h2>Leaky bucket algorithm</h2>
 * Imagine a bucket with a hole in the bottom: water (requests) flows in at any rate, but leaks
 * out at a fixed rate. A request is admitted only if the bucket has room — i.e., the current
 * in-flight/queued volume (after subtracting what has leaked since the last call) is below
 * {@code capacity}. The bucket never "overflows" — excess requests are simply rejected.
 *
 * <p>This implementation treats the "water level" as a {@code double}: it rises by {@code n}
 * on each successful admit, and drains continuously at {@code leakPerNano} per nanosecond. We
 * compute the drained volume lazily on each call (like the token bucket's lazy refill) rather
 * than running a background drainer thread.
 *
 * <h2>Leaky bucket vs. token bucket</h2>
 * <table border="1">
 *   <tr><th>Property</th><th>Token bucket</th><th>Leaky bucket</th></tr>
 *   <tr><td>Bursts</td><td>Allowed up to {@code capacity}</td><td>Smoothed — no bursts</td></tr>
 *   <tr><td>Output rate</td><td>Variable, up to rate × capacity</td><td>Constant at leak rate</td></tr>
 *   <tr><td>Use case</td><td>Public APIs — short bursts OK</td><td>Network shaping / traffic policing where downstream cannot tolerate burstiness (e.g., a video encoder)</td></tr>
 *   <tr><td>Conceptual model</td><td>Credits accumulate and are spent</td><td>Water pours in, drains at a fixed rate</td></tr>
 * </table>
 *
 * <p>Put differently: a token bucket says "you may borrow future capacity from the refill
 * stream"; a leaky bucket says "you may only go as fast as the drain can keep up". Both allow
 * the same long-run average rate, but the leaky bucket imposes a hard ceiling on instantaneous
 * throughput.
 *
 * <h2>Implementation notes — same structural pattern as {@link TokenBucketRateLimiter}</h2>
 * <ol>
 *   <li><b>Compound-state CAS</b> — {@code level} (double) and {@code lastLeakNanos} (long) are
 *       packed into an immutable record held in an {@link AtomicReference}. Updating both fields
 *       atomically avoids the TOCTOU race that two separate atomics would suffer.</li>
 *   <li><b>Lazy drain</b> — on each call we compute {@code elapsed × leakPerNano} and subtract
 *       it from the stored level. No background timer, no scheduler, idle keys cost nothing.</li>
 *   <li><b>Lock-free CAS retry loop</b> — progress is guaranteed (one thread wins each round),
 *       no thread is ever parked on a monitor. See {@link TokenBucketRateLimiter} for the
 *       detailed rationale on CAS vs {@code synchronized}.</li>
 * </ol>
 *
 * <h2>Why not {@code synchronized}?</h2>
 * See {@link TokenBucketRateLimiter} — the arguments are identical: kernel context switches on
 * contention, priority inversion, and throughput ceiling on a single mutex. CAS keeps all state
 * transitions in user space.
 *
 * <h2>Scaling out</h2>
 * The same per-key leaky bucket can be moved to Redis using a Lua script that atomically reads
 * the current level and last-drain timestamp, computes the drain, and conditionally updates. This
 * gives cluster-wide rate limiting without coordination between application nodes.
 */
public class LeakyBucketRateLimiter implements RateLimiter {

    /**
     * Immutable per-key state. Using a record enforces value semantics: the CAS loop can only
     * swap the whole tuple, never mutate one field independently.
     *
     * @param level          current water level in the bucket (0.0 = empty, capacity = full).
     *                       Stored as {@code double} so partial-nanosecond drain volumes
     *                       accumulate correctly without rounding to zero on small time deltas.
     * @param lastLeakNanos  the clock value when we last applied a drain. The difference between
     *                       the current clock and this value is the elapsed window used to
     *                       compute how much has leaked since the last call.
     */
    private record BucketState(double level, long lastLeakNanos) {}

    private final double capacity;
    private final double leakPerNano;
    private final LongSupplier clock;
    private final Map<String, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();

    /**
     * Convenience constructor using the real monotonic clock.
     *
     * @param capacity    maximum water level; requests are rejected when the bucket is full.
     * @param leakPerSec  drain rate in requests per second.
     */
    public LeakyBucketRateLimiter(double capacity, double leakPerSec) {
        this(capacity, leakPerSec, System::nanoTime);
    }

    /**
     * @param capacity    maximum bucket volume; must be positive.
     * @param leakPerSec  drain rate in units per second; must be positive.
     * @param clock       injectable monotonic time source. Tests pass an {@code AtomicLong} driven
     *                    deterministically. Production uses {@link System#nanoTime()} — never
     *                    {@link System#currentTimeMillis()}, which is wall-clock and can jump
     *                    backwards on NTP adjustments or manual time changes, corrupting the
     *                    elapsed-drain calculation.
     */
    public LeakyBucketRateLimiter(double capacity, double leakPerSec, LongSupplier clock) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (leakPerSec <= 0) throw new IllegalArgumentException("leakPerSec must be positive");
        this.capacity = capacity;
        // Precompute units-per-nanosecond once. Division by 1e9 in every hot-path call would
        // accumulate floating-point error and wastes a CPU division; a multiply is cheaper.
        this.leakPerNano = leakPerSec / 1_000_000_000d;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        // If n > capacity the bucket can never hold this much even when empty — reject fast
        // rather than spinning forever in the CAS loop.
        if (n > capacity) return false;

        long now = clock.getAsLong();
        // ConcurrentHashMap.computeIfAbsent: the factory runs at most once per missing key
        // even under concurrent first-touch. New buckets start empty (level = 0.0), meaning
        // the first caller always succeeds up to capacity — the conventional "benefit of the
        // doubt" treatment.
        AtomicReference<BucketState> ref = buckets.computeIfAbsent(
                key, k -> new AtomicReference<>(new BucketState(0.0, now)));

        // ---- Lock-free CAS retry loop ----
        while (true) {
            // (1) Snapshot — BucketState is immutable so this view is stable.
            BucketState current = ref.get();

            // Defensive clamp: nanoTime() is monotonic per JVM but historical cross-thread
            // anomalies have been observed on some platforms. Clamping to >= 0 prevents a
            // negative elapsed value from inflating the level.
            long elapsed = Math.max(0, now - current.lastLeakNanos());

            // Lazy drain: subtract what has leaked since the last call, floor at 0.0.
            // An idle bucket never goes negative (Math.max) and never exceeds capacity by
            // draining below zero.
            double newLevel = Math.max(0.0, current.level() - elapsed * leakPerNano);

            // (2) Can we fit n more units without exceeding the bucket's capacity?
            if (newLevel + n > capacity) return false;  // bucket is too full — reject

            // (3) Admit: raise the level by n and stamp the current time as the new drain
            // baseline. The next call's drain starts from this moment.
            BucketState next = new BucketState(newLevel + n, now);

            // (4) CAS — if another thread updated the bucket between our read and now, we
            // re-read and retry. On success we've atomically committed drain + admission.
            if (ref.compareAndSet(current, next)) return true;
            // CAS lost — loop and retry with the fresher state.
        }
    }
}
