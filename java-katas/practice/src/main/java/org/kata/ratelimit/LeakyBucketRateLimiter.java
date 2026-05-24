package org.kata.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

public class LeakyBucketRateLimiter implements RateLimiter {

    public LeakyBucketRateLimiter(double capacity, double leakPerSec) {
        throw new UnsupportedOperationException();
    }

    public LeakyBucketRateLimiter(double capacity, double leakPerSec, LongSupplier clock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryAcquire(String key, int n) {
        throw new UnsupportedOperationException();
    }
}
