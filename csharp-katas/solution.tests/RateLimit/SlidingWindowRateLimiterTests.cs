using Katas.RateLimit;
using Microsoft.Extensions.Time.Testing;

namespace Katas.Tests.RateLimit;

public sealed class SlidingWindowRateLimiterTests
{
    // ── Capacity enforcement ───────────────────────────────────────────────

    [Fact]
    public void TryAcquire_Should_AllowUpToMaxRequests()
    {
        var limiter = new SlidingWindowRateLimiter(maxRequests: 3, window: TimeSpan.FromSeconds(10));

        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
    }

    [Fact]
    public void TryAcquire_Should_RejectOnceMaxRequestsExceeded()
    {
        var limiter = new SlidingWindowRateLimiter(maxRequests: 2, window: TimeSpan.FromSeconds(10));

        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());
    }

    // ── Sliding window restores capacity ──────────────────────────────────

    [Fact]
    public void TryAcquire_Should_AllowRequestsAfterWindowSlides()
    {
        var fake = new FakeTimeProvider();
        var limiter = new SlidingWindowRateLimiter(maxRequests: 2, window: TimeSpan.FromSeconds(1), fake);

        // Fill the window.
        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());

        // Advance a full window — previous window count is now the "previous" bucket.
        // Advance another full window so the previous-window weight drops to zero.
        fake.Advance(TimeSpan.FromSeconds(2));

        // Previous window is now two windows ago → previous count = 0 → full capacity.
        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());
    }

    [Fact]
    public void TryAcquire_Should_BlendPreviousWindowWeightPartway()
    {
        var fake = new FakeTimeProvider();
        // maxRequests = 4, window = 1 s.
        // At t=0: send 4 requests → bucket full.
        // Advance to t=0.5 s (50% into new window).
        // Weighted estimate = 4 * (1 − 0.5) + 0 = 2 → 2 more requests are available.
        var limiter = new SlidingWindowRateLimiter(maxRequests: 4, window: TimeSpan.FromSeconds(1), fake);

        for (int i = 0; i < 4; i++) Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());

        fake.Advance(TimeSpan.FromSeconds(1)); // enter new window
        fake.Advance(TimeSpan.FromMilliseconds(500)); // 50% through it

        Assert.True(limiter.TryAcquire());
        Assert.True(limiter.TryAcquire());
        Assert.False(limiter.TryAcquire());
    }

    // ── Per-limiter independence ───────────────────────────────────────────

    [Fact]
    public void TryAcquire_Should_NotShareStateBetweenInstances()
    {
        var fake = new FakeTimeProvider();
        var a = new SlidingWindowRateLimiter(1, TimeSpan.FromSeconds(5), fake);
        var b = new SlidingWindowRateLimiter(1, TimeSpan.FromSeconds(5), fake);

        Assert.True(a.TryAcquire());
        Assert.False(a.TryAcquire());
        Assert.True(b.TryAcquire());
    }

    // ── Concurrency ───────────────────────────────────────────────────────

    [Fact]
    public void TryAcquire_Should_NeverGrantMoreThanMaxRequestsUnderConcurrentLoad()
    {
        const int maxRequests = 10;
        var fake = new FakeTimeProvider(); // frozen — no window advancement
        var limiter = new SlidingWindowRateLimiter(maxRequests, TimeSpan.FromSeconds(60), fake);

        int granted = 0;
        Parallel.For(0, 200, _ =>
        {
            if (limiter.TryAcquire())
                Interlocked.Increment(ref granted);
        });

        Assert.Equal(maxRequests, granted);
    }
}
