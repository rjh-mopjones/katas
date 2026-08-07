package org.kata.slidingwindow;

import java.util.OptionalDouble;
import java.util.function.LongSupplier;

public final class SlidingWindow {

    public SlidingWindow(long windowMillis, LongSupplier nowMillis) {
        throw new UnsupportedOperationException();
    }

    public boolean add(long tsMillis, double value) {
        throw new UnsupportedOperationException();
    }

    public boolean add(long tsMillis, double value, double weight) {
        throw new UnsupportedOperationException();
    }

    public long count() {
        throw new UnsupportedOperationException();
    }

    public double sum() {
        throw new UnsupportedOperationException();
    }

    public OptionalDouble weightedAverage() {
        throw new UnsupportedOperationException();
    }

    public int retainedBuckets() {
        throw new UnsupportedOperationException();
    }
}
