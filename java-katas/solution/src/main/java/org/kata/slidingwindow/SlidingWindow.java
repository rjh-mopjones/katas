package org.kata.slidingwindow;

import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Rolling aggregate (count / sum / weighted average) over a fixed-length time window.
 *
 * <p>The kind of thing behind a rolling traded-volume, a moving-average price, or a rate limiter:
 * values arrive stamped with a millisecond timestamp and each read reports only those still inside
 * the last {@code windowMillis} of the injected clock. Time is a {@link LongSupplier} of
 * <em>millis</em> (not {@code System.currentTimeMillis()} inline) so tests can drive it with an
 * {@link java.util.concurrent.atomic.AtomicLong}.
 *
 * <h2>Window boundary</h2>
 * A value with timestamp {@code ts} is in the window at query time {@code now} iff
 * {@code now - windowMillis < ts <= now}. The trailing edge is <b>exclusive</b> ({@code >}) and the
 * leading edge <b>inclusive</b> ({@code <=}); a value exactly {@code windowMillis} old has just
 * expired. Committing to (and testing) that rule is half the exercise — off-by-one here is a silent
 * data bug, not a crash.
 *
 * <h2>Data structure</h2>
 * A {@link TreeMap} keyed by millisecond timestamp whose value is a per-millisecond {@code Bucket}
 * aggregate ({@code count}, {@code sum}, {@code weight}, {@code weighted = Σ value·weight}). Four
 * running totals mirror the live buckets so every read is O(1) amortised rather than an O(n) rescan.
 * {@code evict(now)} prunes buckets past the trailing edge — subtracting them from the totals — and
 * runs on every add and every read (lazy eviction; there is no background thread).
 *
 * <p>Keying by <b>timestamp</b> rather than arrival order is what makes late events correct: an
 * out-of-order-but-in-window value merges into its millisecond bucket; a value already past the
 * trailing edge is rejected ({@code add} returns {@code false}); duplicate timestamps simply
 * aggregate. Nothing on the read path depends on the order values arrived — a naive design that
 * evicted "from the head" of an arrival-ordered queue breaks the moment timestamps arrive
 * out of order.
 *
 * <h2>Memory bound</h2>
 * Because a bucket is <em>one millisecond of aggregate</em>, not a list of raw events, retention is
 * O({@code windowMillis}) buckets regardless of event <em>rate</em>: a million events in the same
 * millisecond collapse into a single bucket. {@link #retainedBuckets()} exposes the live bucket count
 * so the bound is testable. Coarser buckets (say per-second) would shrink memory further at the cost
 * of accuracy to the bucket edge — the granularity/accuracy trade-off to name out loud.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Percentiles</b> over the window — swap the scalar bucket for a bucketed histogram or
 *       t-digest.</li>
 *   <li><b>Count-based window</b> (last N events) instead of time-based — the structure changes to a
 *       ring/deque of events since the boundary is now ordinal, not temporal.</li>
 *   <li><b>Concurrency</b> — many producers; guard the totals + map with a single lock, or shard by
 *       key and merge on read.</li>
 * </ul>
 */
public final class SlidingWindow {

    private static final class Bucket {
        long count;
        double sum;
        double weight;
        double weighted;
    }

    private final long windowMillis;
    private final LongSupplier now;

    private final TreeMap<Long, Bucket> buckets = new TreeMap<>();
    private long totalCount;
    private double totalSum;
    private double totalWeight;
    private double totalWeighted;

    public SlidingWindow(long windowMillis, LongSupplier nowMillis) {
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis must be positive: " + windowMillis);
        }
        this.windowMillis = windowMillis;
        this.now = nowMillis;
    }

    /** Drop buckets past the trailing edge (ts &lt;= now - window), keeping the running totals in sync. */
    private void evict(long nowMs) {
        long edge = nowMs - windowMillis; // in-window iff ts > edge
        Iterator<Map.Entry<Long, Bucket>> it = buckets.headMap(edge, true).entrySet().iterator();
        while (it.hasNext()) {
            Bucket b = it.next().getValue();
            totalCount -= b.count;
            totalSum -= b.sum;
            totalWeight -= b.weight;
            totalWeighted -= b.weighted;
            it.remove();
        }
    }

    /** Record {@code value} at {@code tsMillis} with unit weight. */
    public boolean add(long tsMillis, double value) {
        return add(tsMillis, value, 1.0);
    }

    /**
     * Record {@code value} weighted by {@code weight} at {@code tsMillis}.
     *
     * @return {@code false} (and nothing is recorded) if {@code tsMillis} is in the future or already
     *     past the trailing edge; {@code true} if it landed inside the current window.
     */
    public boolean add(long tsMillis, double value, double weight) {
        long nowMs = now.getAsLong();
        evict(nowMs);
        if (tsMillis > nowMs || tsMillis <= nowMs - windowMillis) {
            return false;
        }
        Bucket b = buckets.computeIfAbsent(tsMillis, k -> new Bucket());
        b.count++;
        b.sum += value;
        b.weight += weight;
        b.weighted += value * weight;
        totalCount++;
        totalSum += value;
        totalWeight += weight;
        totalWeighted += value * weight;
        return true;
    }

    /** Number of values currently inside the window. */
    public long count() {
        evict(now.getAsLong());
        return totalCount;
    }

    /** Sum of values currently inside the window ({@code 0.0} when empty). */
    public double sum() {
        evict(now.getAsLong());
        return totalSum;
    }

    /**
     * Weighted average {@code Σ(value·weight) / Σ(weight)} over the window, or
     * {@link OptionalDouble#empty()} when the window is empty or total weight is zero (no divide-by-zero).
     */
    public OptionalDouble weightedAverage() {
        evict(now.getAsLong());
        return totalWeight == 0.0 ? OptionalDouble.empty() : OptionalDouble.of(totalWeighted / totalWeight);
    }

    /** Live retention buckets — bounded by the window length, independent of event rate. */
    public int retainedBuckets() {
        evict(now.getAsLong());
        return buckets.size();
    }
}
