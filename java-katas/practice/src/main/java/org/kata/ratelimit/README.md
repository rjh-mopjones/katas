# Rate Limiters

> Implement three classic rate-limiting algorithms lock-free, per-key, with an injectable clock.

## The problem
A backend service needs to cap request throughput per client key. Implement three distinct algorithms — token bucket, leaky bucket, and sliding window counter — all sharing the same `RateLimiter` interface. Each must be thread-safe without using locks, and must support a deterministic clock for testing.

## Requirements
- `tryAcquire(key, n)` returns `true` if `n` tokens can be consumed for `key` right now, `false` otherwise. `tryAcquire(key)` defaults to `n=1`.
- New keys are initialised on first access (token bucket: full; leaky bucket: empty; sliding window: zero count).
- Requesting more than the configured capacity/limit must return `false` immediately without spinning.
- The clock is injectable (`LongSupplier`) and must use monotonic time (`System::nanoTime`) in production — never wall-clock time, which can jump backwards.
- All state updates must be lock-free (no mutual-exclusion locks) and thread-safe.

**Token bucket**: refills tokens at a configured rate (tokens/sec) capped at capacity. A burst of up to `capacity` tokens is allowed.

**Leaky bucket**: maintains a water level that drains at a fixed rate. Admits a request only if `level + n <= capacity`. Produces a smooth output; no bursts.

**Sliding window counter**: approximates a true sliding window at O(1) memory per key, eliminating the boundary-spike problem of fixed windows.

## What you implement
Implement `TokenBucketRateLimiter`, `LeakyBucketRateLimiter`, and `SlidingWindowRateLimiter` from scratch — the `RateLimiter` public API (`tryAcquire`). You design the internal state and concurrency mechanism yourself.

(`RateLimiter` is provided as a working fixture.)

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/ratelimit/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
