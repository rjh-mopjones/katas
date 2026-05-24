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
        if (n <= 0) throw new IllegalArgumentException("n must be positive");
        // A request larger than the limit can never be satisfied — fail fast.
        if (n > limit) return false;

        long now = clock.getAsLong();
        // computeIfAbsent: at most one WindowState is created per key even under concurrent
        // first-touch. windowStart = now means the first request initialises the window at
        // the moment of first use.
        AtomicReference<WindowState> ref = windows.computeIfAbsent(
                key, k -> new AtomicReference<>(new WindowState(0L, 0L, now)));

        // ---- Lock-free CAS retry loop ----
        while (true) {
            // (1) Snapshot the current state — immutable so no other thread can mutate it.
            WindowState current = ref.get();

            // (2) Determine which window 'now' falls into by computing how far past the current
            //     window's start we are. Integer division gives us the number of completed windows.
            long elapsed = Math.max(0, now - current.windowStart());
            long windowsElapsed = elapsed / windowNanos;

            // Age the state forward: slide the window boundary to match 'now'.
            long prevCount;
            long currCount;
            long newWindowStart;

            if (windowsElapsed == 0) {
                // Still inside the same fixed window — no aging needed.
                prevCount = current.prevCount();
                currCount = current.currCount();
                newWindowStart = current.windowStart();
            } else if (windowsElapsed == 1) {
                // One window has elapsed. The window that was 'current' is now 'previous'.
                // A new current window starts at the exact boundary, not at 'now', so that
                // window size stays fixed. This prevents the window boundaries from drifting.
                prevCount = current.currCount();
                currCount = 0L;
                newWindowStart = current.windowStart() + windowNanos;
            } else {
                // Two or more windows have elapsed. The previous window's traffic is entirely
                // outside the trailing window — reset both counters.
                prevCount = 0L;
                currCount = 0L;
                // Snap the start to the most recent window boundary so elapsed math stays correct.
                newWindowStart = now - (elapsed % windowNanos);
            }

            // (3) Compute the sliding window estimate.
            //     How far through the current window are we? This fraction (0..1) tells us
            //     what portion of the *previous* window is still within the trailing window.
            //     Example: 30% through the current window → 70% of the previous window still
            //     overlaps with the trailing window of the same size.
            double elapsedInCurrent = (double) (now - newWindowStart);
            double fractionOfCurrent = Math.min(1.0, elapsedInCurrent / windowNanos);
            double prevWindowWeight = 1.0 - fractionOfCurrent;

            double estimatedCount = prevCount * prevWindowWeight + currCount;

            // (4) Would admitting n requests exceed the limit?
            if (estimatedCount + n > limit) return false;

            // (5) Propose next state: increment the current-window counter by n.
            WindowState next = new WindowState(prevCount, currCount + n, newWindowStart);

            // (6) CAS — if another thread raced ahead, re-read and retry.
            if (ref.compareAndSet(current, next)) return true;
            // CAS lost — another thread updated. Re-read and retry.
        }
    }
}
