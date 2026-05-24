# RateLimit

Implement two thread-safe rate limiters — token bucket and sliding-window counter.

## The problem

Unthrottled callers can overwhelm a service. A rate limiter sits in front and rejects requests that exceed the configured rate, protecting downstream resources.

## Requirements

- `TokenBucketRateLimiter`: tokens accumulate at a fixed rate up to a `capacity` ceiling. Each call to `TryAcquire` first performs a lazy refill (computes elapsed time × refill rate, adds tokens up to capacity), then grants or rejects the request.
- `SlidingWindowRateLimiter`: tracks requests in the current and previous fixed windows. Estimates load as `prev × (1 − progress) + curr` and rejects when the estimate plus the requested permits would exceed `maxRequests`.
- Both must be thread-safe.
- Both accept an optional `TimeProvider` so tests can run under a `FakeTimeProvider` without real sleeps.
- Constructors must throw `ArgumentOutOfRangeException` for invalid arguments.

## What you implement

```csharp
public sealed class TokenBucketRateLimiter : IRateLimiter
{
    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond, TimeProvider? timeProvider = null);
    public bool TryAcquire(int permits = 1);
}

public sealed class SlidingWindowRateLimiter : IRateLimiter
{
    public SlidingWindowRateLimiter(int maxRequests, TimeSpan window, TimeProvider? timeProvider = null);
    public bool TryAcquire(int permits = 1);
}
```

## The real challenge

- Lazy refill: avoid a background timer — compute elapsed time only on `TryAcquire`.
- Thread-safety: a small `lock` around the critical section is fine; understand why a CAS alternative exists.
- Sliding window approximation: the weighted blend prevents boundary spikes but uses only O(1) memory.
- `TimeProvider.GetTimestamp` / `GetElapsedTime`: learn to use the clock abstraction so tests are deterministic.

## Run

Write your own tests under `practice.tests/RateLimit/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~RateLimit"
```

## Reference

- Solution: `solution/RateLimit/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.timeprovider
