package org.kata.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Lock-free, per-key sliding window counter rate limiter.
 *
 * <h2>Algorithm: sliding window counter approximation</h2>
 * The pure <em>sliding window log</em> tracks every request timestamp in a list and counts those
 * within the trailing {@code windowNanos} on each call. It is perfectly accurate but costs O(n)
 * memory per key, where n is the number of requests in the window — unacceptable for keys that
 * receive thousands of requests per second.
 *
 * <p>This implementation uses the <em>sliding window counter</em> approximation, which trades
 * a small amount of accuracy for O(1) memory per key:
 *
 * <pre>
 *  estimatedCount = prevWindowCount × (1 − elapsed/windowSize) + currWindowCount
 * </pre>
 *
 * <p>The intuition: we don't know <em>when</em> in the previous fixed window its requests
 * arrived, so we assume they were uniformly distributed and take the proportion of the previous
 * window that still overlaps with the current trailing window. This is exactly the technique
 * used by Cloudflare and Nginx for their rate limiters.
 *
 * <h2>Why not fixed windows?</h2>
 * A fixed-window counter resets at hard boundaries (e.g., every whole minute). A client can
 * send {@code limit} requests at 00:59 and another {@code limit} at 01:00, for an effective
 * burst of {@code 2 × limit} in two seconds — the "boundary spike" problem. The sliding window
 * counter effectively solves this by weighing in the recent past proportionally.
 *
 * <h2>Accuracy trade-off</h2>
 * The sliding window counter is not perfectly accurate — it can be off by up to ~1 request per
 * window boundary in edge cases. For most real-world rate limiting (APIs, login attempts) this
 * is acceptable. If you need exact accuracy, use the sliding window log at O(n) memory cost,
 * or move to a distributed store like Redis which supports sorted-set-based exact sliding logs
 * with TTL-based eviction.
 *
 * <h2>Algorithm trade-offs at a glance</h2>
 * <table border="1">
 *   <tr><th>Algorithm</th><th>Memory</th><th>Accuracy</th><th>Boundary spike</th></tr>
 *   <tr><td>Fixed window counter</td><td>O(1)</td><td>Exact within window</td><td>Yes — 2× burst possible</td></tr>
 *   <tr><td>Sliding window log</td><td>O(requests/window)</td><td>Exact</td><td>No</td></tr>
 *   <tr><td>Sliding window counter (this)</td><td>O(1)</td><td>Approximate (±1 request)</td><td>No</td></tr>
 *   <tr><td>Token bucket</td><td>O(1)</td><td>Exact</td><td>Controlled bursts up to capacity</td></tr>
 * </table>
 *
 * <h2>State design: two fixed windows + the current offset</h2>
 * We keep track of two consecutive fixed windows:
 * <ul>
 *   <li>{@code prevCount} — request count in the window immediately before the current one.</li>
 *   <li>{@code currCount} — request count in the window that contains {@code now}.</li>
 *   <li>{@code windowStart} — the nanoTime at which the current fixed window began.</li>
 * </ul>
 * When time advances past the end of the current window, we age the counters forward: the
 * current window becomes the previous, and a fresh current window starts at zero. If two or
 * more windows have elapsed since the last call, both counters reset to zero (the previous
 * window's traffic is too old to matter).
 *
 * <h2>Thread safety</h2>
 * All state is packed into an immutable record in an {@link AtomicReference} and updated via a
 * CAS retry loop — the same pattern as {@link TokenBucketRateLimiter}. No lock is held; progress
 * is guaranteed because at least one thread's CAS succeeds per round.
 */
public class SlidingWindowRateLimiter implements RateLimiter {

    /**
     * Immutable per-key state. Records enforce value semantics: the CAS loop swaps the whole
     * tuple atomically. Mutating individual fields would require multiple atomics and introduce
     * the risk of observing a partially-updated state.
     *
     * @param prevCount   request count in the previous fixed window.
     * @param currCount   request count in the current fixed window.
     * @param windowStart nanoTime at which the current window began. Determines which window
     *                    "now" falls into, and how much of the previous window overlaps with the
     *                    trailing sliding window.
     */
    private record WindowState(long prevCount, long currCount, long windowStart) {}

    private final long limit;
    private final long windowNanos;
    private final LongSupplier clock;
    private final Map<String, AtomicReference<WindowState>> windows = new ConcurrentHashMap<>();

    /**
     * Convenience constructor using the real monotonic clock.
     *
     * @param limit       maximum requests allowed within any trailing {@code windowNanos} window.
     * @param windowNanos size of the sliding window in nanoseconds.
     */
    public SlidingWindowRateLimiter(long limit, long windowNanos) {
        this(limit, windowNanos, System::nanoTime);
    }

    /**
     * @param limit       maximum requests allowed; must be positive.
     * @param windowNanos window duration in nanoseconds; must be positive.
     * @param clock       injectable monotonic time source. Tests pass an {@code AtomicLong}
     *                    to advance time without sleeping. Production uses
     *                    {@link System#nanoTime()} — never {@link System#currentTimeMillis()},
     *                    which can jump backwards on NTP adjustments, making the elapsed
     *                    fraction negative and corrupting the weighted estimate.
     */
    public SlidingWindowRateLimiter(long limit, long windowNanos, LongSupplier clock) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        if (windowNanos <= 0) throw new IllegalArgumentException("windowNanos must be positive");
        this.limit = limit;
        this.windowNanos = windowNanos;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int n) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
