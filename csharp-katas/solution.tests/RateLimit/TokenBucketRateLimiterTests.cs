using Katas.RateLimit;
using Microsoft.Extensions.Time.Testing;

namespace Katas.Tests.RateLimit;

public sealed class TokenBucketRateLimiterTests
{
    // ── Capacity enforcement ───────────────────────────────────────────────

    [Fact]
    public void TryAcquire_Should_AllowUpToCapacity()
    {
        var limiter = new TokenBucketRateLimiter(capacity: 3, refillTokensPerSecond: 0.1);

        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
    }

    [Fact]
    public void TryAcquire_Should_RejectWhenBucketEmpty()
    {
        var limiter = new TokenBucketRateLimiter(capacity: 2, refillTokensPerSecond: 0.1);

        limiter.TryAcquire();
        limiter.TryAcquire();

        Assert.False(limiter.TryAcquire());
    }

    [Fact]
    public void TryAcquire_Should_RejectImmediatelyWhenPermitsExceedCapacity()
    {
        var limiter = new TokenBucketRateLimiter(capacity: 5, refillTokensPerSecond: 10);

        // Even a full bucket cannot satisfy a request larger than capacity.
        Assert.False(limiter.TryAcquire(permits: 6));
    }

    [Fact]
    public void TryAcquire_Should_ConsumeMultiplePermitsAtOnce()
    {
        var limiter = new TokenBucketRateLimiter(capacity: 5, refillTokensPerSecond: 0.1);

        Assert.True(limiter.TryAcquire(permits: 5));
        Assert.False(limiter.TryAcquire());
    }

    // ── Refill via FakeTimeProvider ────────────────────────────────────────

    [Fact]
    public void TryAcquire_Should_RefillAfterTimeAdvances()
    {
        var fake = new FakeTimeProvider();
        var limiter = new TokenBucketRateLimiter(capacity: 2, refillTokensPerSecond: 2, fake);

        // Drain the bucket.
        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());

        // Advance by 1 second → 2 new tokens, but capacity is 2.
        fake.Advance(TimeSpan.FromSeconds(1));

        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());
    }

    [Fact]
    public void TryAcquire_Should_ClampRefillAtCapacity()
    {
        var fake = new FakeTimeProvider();
        var limiter = new TokenBucketRateLimiter(capacity: 3, refillTokensPerSecond: 100, fake);

        Assert.True(limiter.TryAcquire(3)); // drain

        // Advance far beyond what it takes to fill the bucket.
        fake.Advance(TimeSpan.FromSeconds(60));

        // Should still only have capacity tokens, not more.
        Assert.True(limiter.TryAcquire(3));
        Assert.False(limiter.TryAcquire());
    }

    [Fact]
    public void TryAcquire_Should_NotRefillBeforeSufficientTimeElapses()
    {
        var fake = new FakeTimeProvider();
        // 1 token per second → need 1 second to get 1 token back.
        var limiter = new TokenBucketRateLimiter(capacity: 1, refillTokensPerSecond: 1, fake);

        Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());

        fake.Advance(TimeSpan.FromMilliseconds(500)); // not enough
        Assert.False(limiter.TryAcquire());

        fake.Advance(TimeSpan.FromMilliseconds(500)); // total 1 s
        Assert.True(limiter.TryAcquire());
    }

    // ── Per-bucket independence ────────────────────────────────────────────

    [Fact]
    public void TryAcquire_Should_NotShareStateBetweenInstances()
    {
        var fake = new FakeTimeProvider();
        var a = new TokenBucketRateLimiter(capacity: 1, refillTokensPerSecond: 1, fake);
        var b = new TokenBucketRateLimiter(capacity: 1, refillTokensPerSecond: 1, fake);

        Assert.True(a.TryAcquire());
        // a is now empty; b should still have a full token.
        Assert.True(b.TryAcquire());
        Assert.False(a.TryAcquire());
    }

    // ── Concurrency — never grants more than capacity ──────────────────────

    [Fact]
    public void TryAcquire_Should_NeverGrantMoreThanCapacityUnderConcurrentLoad()
    {
        const int capacity = 10;
        var fake = new FakeTimeProvider(); // frozen clock — no refill during test
        var limiter = new TokenBucketRateLimiter(capacity, refillTokensPerSecond: 0.001, fake);

        int granted = 0;
        Parallel.For(0, 100, _ =>
        {
            if (limiter.TryAcquire())
                Interlocked.Increment(ref granted);
        });

        Assert.Equal(capacity, granted);
    }
}
