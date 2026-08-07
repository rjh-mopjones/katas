package com.katas.k03_window;

import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Worked reference — passes Stage 1 through Stage 4. See NOTES.md for the pivot each stage forces.
 *
 * <p>One representation covers every stage: a {@link TreeMap} keyed by millisecond timestamp, whose
 * value is a per-millisecond {@link Bucket} aggregate. Running totals make reads O(1) amortised;
 * expired buckets are pruned (and subtracted from the totals) on every add and every read. Because a
 * bucket is one millisecond, memory is O(windowMillis) regardless of event <em>rate</em> — a million
 * events in the same millisecond collapse into one bucket. That is the Stage-4 memory bound, and it
 * also gives exact counts/sums for millisecond-granular timestamps in the earlier stages.
 */
public final class SlidingWindow {

    private static final class Bucket {
        long count;
        double sum;
        double weight;
        double weighted;
    }

    private final long windowMillis;
    @SuppressWarnings("unused") // advisory: max out-of-orderness a fixed-ring variant would size for
    private final long latenessMillis;
    private final LongSupplier now;

    private final TreeMap<Long, Bucket> buckets = new TreeMap<>();
    private long totalCount;
    private double totalSum;
    private double totalWeight;
    private double totalWeighted;

    public SlidingWindow(long windowMillis, LongSupplier nowMillis) {
        this(windowMillis, 0L, nowMillis);
    }

    public SlidingWindow(long windowMillis, long latenessMillis, LongSupplier nowMillis) {
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis must be positive: " + windowMillis);
        }
        this.windowMillis = windowMillis;
        this.latenessMillis = latenessMillis;
        this.now = nowMillis;
    }

    /** Drop buckets that have fallen past the trailing edge (ts <= now - window), keeping totals in sync. */
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

    public boolean add(long tsMillis, double value) {
        return add(tsMillis, value, 1.0);
    }

    public boolean add(long tsMillis, double value, double weight) {
        long nowMs = now.getAsLong();
        evict(nowMs);
        if (tsMillis > nowMs || tsMillis <= nowMs - windowMillis) {
            return false; // in the future, or already past the trailing edge
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

    public long count() {
        evict(now.getAsLong());
        return totalCount;
    }

    public double sum() {
        evict(now.getAsLong());
        return totalSum;
    }

    public OptionalDouble weightedAverage() {
        evict(now.getAsLong());
        return totalWeight == 0.0 ? OptionalDouble.empty() : OptionalDouble.of(totalWeighted / totalWeight);
    }

    public int retainedBuckets() {
        evict(now.getAsLong());
        return buckets.size();
    }
}
