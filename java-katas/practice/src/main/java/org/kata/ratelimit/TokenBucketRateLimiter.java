package org.kata.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

public class TokenBucketRateLimiter implements RateLimiter {

    public TokenBucketRateLimiter(long capacity, double refillPerSec) {
        throw new UnsupportedOperationException();
    }

    public TokenBucketRateLimiter(long capacity, double refillPerSec, LongSupplier clock) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryAcquire(String key, int n) {
        throw new UnsupportedOperationException();
    }
}
