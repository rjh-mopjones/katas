# Rate Limiters

> Implement three classic rate-limiting algorithms lock-free, per-key, with an injectable clock.

## The problem
A backend service needs to cap request throughput per client key. Implement three distinct algorithms — token bucket, leaky bucket, and sliding window counter — all sharing the same `RateLimiter` interface. Each must be thread-safe without using locks, and must support a deterministic clock for testing.

## Requirements
- `tryAcquire(key, n)` returns `true` if `n` tokens can be consumed for `key` right now, `false` otherwise. `tryAcquire(key)` defaults to `n=1`.
- New keys are initialised on first access (token bucket: full; leaky bucket: empty; sliding window: zero count).
- Requesting more than the configured capacity/limit must return `false` immediately without spinning.
- The clock is injectable (`LongSupplier`) and must use monotonic time (`System::nanoTime`) in production — never wall-clock time, which can jump backwards.
- All state updates must be lock-free via `AtomicReference` CAS retry loops on an immutable state record.

**Token bucket**: refills tokens lazily at a configured rate (tokens/sec) capped at capacity. A burst of up to `capacity` tokens is allowed.

**Leaky bucket**: maintains a water level that drains lazily at a fixed rate. Admits a request only if `level + n <= capacity`. Produces a smooth output; no bursts.

**Sliding window counter**: approximates the sliding window using two consecutive fixed windows and the formula `prevCount × (1 − elapsed/windowSize) + currCount`. O(1) memory per key. Eliminates the boundary-spike problem of fixed windows.

## What you implement
Implement `TokenBucketRateLimiter`, `LeakyBucketRateLimiter`, and `SlidingWindowRateLimiter` from scratch — the `RateLimiter` public API (`tryAcquire`). You design the internal state and concurrency mechanism yourself.

(`RateLimiter` is provided as a working fixture.)

## The real challenge
- **Compound-state CAS**: both fields of each state record (`tokens`+`lastRefillNanos`, `level`+`lastLeakNanos`, `prevCount`+`currCount`+`windowStart`) must be swapped atomically as one immutable record in an `AtomicReference`. Using two separate `AtomicLong`s would allow a reader to observe a half-updated pair, silently double-refilling or double-draining.
- **Lazy time-based update**: no background thread — derive how much should have accumulated/leaked since `lastNanos` on every call. An idle key costs zero CPU between calls.
- **Fast-reject under load**: early-exit without attempting a CAS when the request is already known to fail. Under heavy rejection this prevents the CAS itself from becoming a contention bottleneck.
- **Algorithm contrast**: token bucket allows bursts up to capacity; leaky bucket enforces a hard throughput ceiling with no burst headroom; sliding window counter prevents boundary spikes at O(1) memory with a small approximation error.

## Run
```
mvn -pl practice test -Dtest=TokenBucketRateLimiterTest,LeakyBucketRateLimiterTest,SlidingWindowRateLimiterTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/ratelimit/`
- Java Interview Primer: Q239 (rate-limiting algorithms), Q43 (atomic classes), Q241 (CAS)
