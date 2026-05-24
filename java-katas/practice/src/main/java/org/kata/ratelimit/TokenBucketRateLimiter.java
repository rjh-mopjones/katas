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
        throw new UnsupportedOperationException("TODO: implement");
    }
}
