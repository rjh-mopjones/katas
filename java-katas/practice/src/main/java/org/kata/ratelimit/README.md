# Rate Limiters

> Implement three classic rate-limiting algorithms lock-free, per-key, with an injectable clock.

## The problem
A backend service needs to cap request throughput per client key. Implement three distinct algorithms — token bucket, leaky bucket, and sliding window counter — all sharing the same rate-limiter contract. Each must be thread-safe without using locks, and must support a deterministic clock for testing.

## Requirements
- Checking whether a given number of units can be consumed for a key right now returns yes or no immediately, without blocking or spinning; checking for a single unit is the common case.
- A key seen for the first time is initialised to each algorithm's natural starting state (token bucket starts full; leaky bucket starts empty; sliding window starts at zero count).
- Requesting more than the configured capacity/limit for a key must never succeed, and must be rejected immediately.
- Time comes from an injectable clock and must use monotonic time in production — never wall-clock time, which can jump backwards.
- All state updates must be lock-free (no mutual-exclusion locks) and thread-safe.

**Token bucket**: refills tokens at a configured rate (tokens/sec) capped at capacity. A burst of up to `capacity` tokens is allowed.

**Leaky bucket**: maintains a water level that drains at a fixed rate. Admits a request only if the resulting level would stay within capacity. Produces a smooth output; no bursts.

**Sliding window counter**: approximates a true sliding window at O(1) memory per key, eliminating the boundary-spike problem of fixed windows.

## What you're given
- `RateLimiter` — the interface all three limiters implement, exposing an acquire-check per key (with a single-unit convenience default).

The three limiter classes already declare that they implement `RateLimiter`; you design and build the internal state and concurrency mechanism for each from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/ratelimit/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
