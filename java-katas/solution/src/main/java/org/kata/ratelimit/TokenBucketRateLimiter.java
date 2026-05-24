package org.kata.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Lock-free, per-key token bucket rate limiter.
 *
 * <p>This is the canonical implementation interviewers like to probe because it combines three
 * patterns worth knowing cold:
 * <ol>
 *   <li><b>Compound-state CAS</b> on an immutable record held in an {@link AtomicReference}.
 *       Both fields ({@code tokens}, {@code lastRefillNanos}) are swapped together as one atomic
 *       step. Using two separate {@code AtomicLong}s would be a correctness bug — a reader could
 *       observe a fresh {@code tokens} value paired with a stale {@code lastRefillNanos} (or
 *       vice-versa) and double-refill. Bundling state in a record forces the atomic update of
 *       the whole tuple.</li>
 *   <li><b>Lazy refill</b>. There is no background thread ticking tokens in. On each call we
 *       derive how many tokens should have arrived since {@code lastRefillNanos} from the
 *       elapsed wall time, capped at {@code capacity}. This avoids a scheduler, avoids cross-key
 *       clock coordination, and means an idle bucket costs zero CPU.</li>
 *   <li><b>Lock-free CAS retry loop</b>. No thread is ever blocked on a monitor; under
 *       contention threads simply re-read and retry. Progress is guaranteed (at least one
 *       thread's CAS always wins per round), and throughput scales with cores instead of
 *       serialising on a single lock.</li>
 * </ol>
 *
 * <p><b>Why not {@code synchronized}?</b> A single mutex per bucket would serialise all readers,
 * park threads on contention (kernel transition cost), and risk priority inversion. CAS keeps
 * everything in user-space and only the loser pays — and only by re-doing a few nanoseconds of
 * work.
 *
 * <p><b>Scaling out.</b> The same algorithm generalises to distributed rate limiting:
 * <ul>
 *   <li>Redis {@code INCR} + {@code EXPIRE} for a simple fixed-window counter.</li>
 *   <li>A Redis Lua script for the compound-state token bucket (atomic read-refill-decrement
 *       inside Redis, equivalent to our CAS but server-side).</li>
 *   <li>Hashed sharding by key when one Redis node is no longer enough.</li>
 * </ul>
 */
public class TokenBucketRateLimiter implements RateLimiter {

    /**
     * Immutable bucket state. Records give us value semantics and a compiler-enforced guarantee
     * that we can only ever swap the whole tuple — never mutate one field in place. That is what
     * makes the CAS sound.
     */
    private record BucketState(long tokens, long lastRefillNanos) {}

    private final long capacity;
    private final double refillPerNano;
    private final LongSupplier clock;
    private final Map<String, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(long capacity, double refillPerSec) {
        this(capacity, refillPerSec, System::nanoTime);
    }

    /**
     * @param clock injectable monotonic time source. Tests pass a fake clock to drive refill
     *              deterministically. Production uses {@link System#nanoTime()} — never
     *              {@link System#currentTimeMillis()}, which is wall-clock and can jump
     *              backwards (NTP slew, manual time changes, leap-second handling) and silently
     *              corrupt the refill math.
     */
    public TokenBucketRateLimiter(long capacity, double refillPerSec, LongSupplier clock) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (refillPerSec <= 0) throw new IllegalArgumentException("refillPerSec must be positive");
        this.capacity = capacity;
        // Precompute tokens-per-nanosecond once. Multiplication in the hot path is cheaper than
        // dividing by 1e9 on every call, and keeps the unit consistent with nanoTime().
        this.refillPerNano = refillPerSec / 1_000_000_000d;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        // Asking for more than the bucket can ever hold is unsatisfiable — reject immediately
        // rather than entering the CAS loop and spinning forever.
        if (n > capacity) return false;

        long now = clock.getAsLong();
        // ConcurrentHashMap.computeIfAbsent gives us atomic lazy initialisation per key: the
        // factory runs at most once per missing key even under concurrent first-touch. New
        // buckets start full, which is the conventional choice (clients aren't penalised for
        // having never been seen before).
        AtomicReference<BucketState> ref = buckets.computeIfAbsent(
                key, k -> new AtomicReference<>(new BucketState(capacity, now)));

        // ---- Lock-free CAS retry loop ----
        // Each iteration: (1) snapshot state, (2) compute the proposed next state, (3) CAS.
        // If CAS fails it means another thread won the race for this bucket — we re-read the
        // now-fresher state and try again. Retries are bounded in practice: contention on a
        // single key is rare, and even worst-case the loop costs a few nanos per attempt.
        while (true) {
            // (1) Snapshot. After this read, `current` is a stable immutable view; no other
            // thread can mutate it because BucketState is a record.
            BucketState current = ref.get();

            // Defensive clamp against clock skew. nanoTime() is guaranteed monotonic *per
            // thread* on most JVMs, but cross-thread monotonicity has had bugs historically on
            // some platforms. Clamping to >=0 means a hiccup costs us at most one missed refill
            // tick rather than producing a negative elapsed time and corrupting the bucket.
            long elapsed = Math.max(0, now - current.lastRefillNanos());

            // Lazy refill: how many tokens have notionally arrived since we last looked,
            // capped at capacity so an idle bucket doesn't accumulate infinite credit.
            long refilled = Math.min(capacity, current.tokens() + (long) (elapsed * refillPerNano));

            // Early-reject fast path. If even after refill we don't have enough tokens, bail
            // out *without* attempting a CAS. This matters under heavy load: a flood of
            // rejected requests must not contend on the bucket's atomic — otherwise rejection
            // itself becomes a scalability bottleneck. Successful acquisitions are the only
            // writers; failures are pure reads.
            if (refilled < n) return false;

            // (2) Propose next state: deduct n, stamp this attempt's `now` as the new refill
            // baseline so the next call's elapsed window starts here.
            BucketState next = new BucketState(refilled - n, now);

            // (3) CAS. Succeeds iff no other thread has touched this bucket since our snapshot.
            // On success we've atomically committed the refill + deduction. On failure another
            // thread won the race — loop and retry with the fresher state.
            if (ref.compareAndSet(current, next)) return true;
            // CAS lost — another thread updated. Retry.
        }
    }
}
