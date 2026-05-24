package org.kata.ratelimit;

/**
 * A per-key rate limiter abstraction.
 *
 * <p>Implementations decide the algorithm (token bucket, leaky bucket, fixed/sliding window) and
 * the storage strategy (in-process map, Redis, etc.). The contract here is deliberately narrow —
 * just "can I do this work right now?" — which keeps callers framework-agnostic and lets the
 * limiter be swapped out (e.g. in-process for tests, Redis-backed for prod) without touching call
 * sites.
 *
 * <p>Algorithm trade-offs at a glance:
 * <ul>
 *   <li><b>Token bucket</b> — refill N/sec up to a capacity C. Allows controlled bursts up to C.
 *       Best fit for public API rate limits where short bursts are acceptable.</li>
 *   <li><b>Leaky bucket</b> — drain at a fixed rate regardless of input. Produces a perfectly
 *       smooth output stream; good for network shaping / traffic policing where downstream
 *       cannot tolerate bursts.</li>
 *   <li><b>Fixed window</b> — counter that resets at wall-clock boundaries. Cheap but suffers
 *       from the "boundary spike" problem: a client can fire 2×limit by stacking requests around
 *       the reset instant.</li>
 *   <li><b>Sliding window log</b> — store the timestamp of every request and count those inside
 *       the trailing window. Most accurate, but O(n) memory per key and expensive at scale.</li>
 * </ul>
 */
public interface RateLimiter {
    /** Try to consume one token for {@code key}. */
    default boolean tryAcquire(String key) { return tryAcquire(key, 1); }

    /** Try to consume {@code n} tokens for {@code key}. Returns false if not enough available. */
    boolean tryAcquire(String key, int n);
}
