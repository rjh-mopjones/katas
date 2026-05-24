package org.kata.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

public class SlidingWindowRateLimiter implements RateLimiter {

    public SlidingWindowRateLimiter(long limit, long windowNanos) {
        throw new UnsupportedOperationException();
    }

    public SlidingWindowRateLimiter(long limit, long windowNanos, LongSupplier clock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryAcquire(String key, int n) {
        throw new UnsupportedOperationException();
    }
}
