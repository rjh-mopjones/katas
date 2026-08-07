package com.katas.k03_window;

import java.util.OptionalDouble;
import java.util.function.LongSupplier;

/**
 * A sliding time window over a stream of timestamped, optionally weighted values.
 *
 * <p>The window covers the last {@code windowMillis}: a value with timestamp {@code ts} is in the
 * window at query time iff {@code now - windowMillis < ts <= now}, where {@code now} comes from the
 * injected clock. All read methods evaluate lazily against the clock, so expired values drop as time
 * advances.
 *
 * <p>TODO: implement every method. They all throw until you do.
 */
public final class SlidingWindow {

    /** Window of {@code windowMillis}, reading the current time from {@code nowMillis}. */
    public SlidingWindow(long windowMillis, LongSupplier nowMillis) {
        this(windowMillis, 0L, nowMillis);
    }

    /**
     * @param windowMillis   window length in milliseconds
     * @param latenessMillis how far out of order a value may arrive and still be handled
     * @param nowMillis      the clock — current time in milliseconds (inject it so tests are deterministic)
     */
    public SlidingWindow(long windowMillis, long latenessMillis, LongSupplier nowMillis) {
        // TODO
    }

    /** Add a value with weight 1. */
    public boolean add(long tsMillis, double value) {
        return add(tsMillis, value, 1.0);
    }

    /**
     * Add a timestamped, weighted value.
     *
     * @return {@code true} if it was accepted into the window; {@code false} if it is too old (already
     *         past the window's trailing edge) or in the future
     */
    public boolean add(long tsMillis, double value, double weight) {
        throw new UnsupportedOperationException("TODO");
    }

    /** Number of values currently in the window. */
    public long count() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Sum of the values currently in the window. */
    public double sum() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Weighted average {@code sum(value*weight)/sum(weight)} over the window, or empty if the total weight is zero. */
    public OptionalDouble weightedAverage() {
        throw new UnsupportedOperationException("TODO");
    }

    /** Number of internal retention buckets currently held — a hook for asserting bounded memory. */
    public int retainedBuckets() {
        throw new UnsupportedOperationException("TODO");
    }
}
