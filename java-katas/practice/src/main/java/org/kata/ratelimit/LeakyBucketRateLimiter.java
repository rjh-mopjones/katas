package org.kata.ratelimit;

public class LeakyBucketRateLimiter implements RateLimiter {
    @Override
    public boolean tryAquire(String key, int n) {
        return false;
    }
}
