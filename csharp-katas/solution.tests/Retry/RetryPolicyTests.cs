namespace Katas.Tests.Retry;

using Katas.Retry;

public sealed class RetryPolicyTests
{
    // Policy: base=1 s, max=10 s, multiplier=2, no jitter.
    // Delays (attempt 1..6):  1, 2, 4, 8, 10, 10  (capped at 10)
    private static RetryPolicy BuildPolicy(int maxAttempts = 6) =>
        new(maxAttempts, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(10), 2.0, UseJitter: false);

    [Theory]
    [InlineData(1, 1)]   // attempt 1: 1 * 2^0 = 1 s
    [InlineData(2, 2)]   // attempt 2: 1 * 2^1 = 2 s
    [InlineData(3, 4)]   // attempt 3: 1 * 2^2 = 4 s
    [InlineData(4, 8)]   // attempt 4: 1 * 2^3 = 8 s
    [InlineData(5, 10)]  // capped at MaxDelay=10 s
    [InlineData(6, 10)]  // still capped
    public void ComputeDelay_Should_GrowExponentiallyAndCapAtMaxDelay(int attempt, int expectedSeconds)
    {
        RetryPolicy policy = BuildPolicy();
        TimeSpan delay = policy.ComputeDelay(attempt, jitterFraction: 0);
        Assert.Equal(TimeSpan.FromSeconds(expectedSeconds), delay);
    }

    [Fact]
    public void ComputeDelay_Should_ReturnBaseDelay_OnFirstAttempt()
    {
        RetryPolicy policy = BuildPolicy();
        TimeSpan delay = policy.ComputeDelay(1, jitterFraction: 0);
        Assert.Equal(TimeSpan.FromSeconds(1), delay);
    }

    [Fact]
    public void ComputeDelay_Should_ApplyJitter_WhenUseJitterIsTrue()
    {
        var policy = new RetryPolicy(3, TimeSpan.FromSeconds(4), TimeSpan.FromSeconds(10), 1.0, UseJitter: true);
        // jitterFraction=0.5 → delay = 4 s * 0.5 = 2 s
        TimeSpan delay = policy.ComputeDelay(1, jitterFraction: 0.5);
        Assert.Equal(TimeSpan.FromSeconds(2), delay);
    }

    [Fact]
    public void ComputeDelay_Should_BeZero_WhenJitterFractionIsZero_AndJitterEnabled()
    {
        var policy = new RetryPolicy(3, TimeSpan.FromSeconds(4), TimeSpan.FromSeconds(10), 1.0, UseJitter: true);
        TimeSpan delay = policy.ComputeDelay(1, jitterFraction: 0.0);
        Assert.Equal(TimeSpan.Zero, delay);
    }

    [Fact]
    public void ComputeDelay_Should_NotExceedComputedDelay_WhenJitterFractionIsLessThanOne()
    {
        var policy = new RetryPolicy(3, TimeSpan.FromSeconds(8), TimeSpan.FromSeconds(8), 1.0, UseJitter: true);
        TimeSpan baseDelay = policy.ComputeDelay(1, jitterFraction: 0);     // baseline (no jitter applied) = 0
        TimeSpan jitteredDelay = policy.ComputeDelay(1, jitterFraction: 0.9);
        // jittered <= computed-without-jitter raw (8s * 0.9 = 7.2 s)
        Assert.True(jitteredDelay <= TimeSpan.FromSeconds(8));
    }

    [Fact]
    public void RetryPolicy_Should_ThrowArgumentOutOfRangeException_WhenMaxAttemptsIsZero()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            new RetryPolicy(0, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(1), 1.0, false));
    }

    [Fact]
    public void RetryPolicy_Should_ThrowArgumentOutOfRangeException_WhenMaxDelayLessThanBaseDelay()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            new RetryPolicy(3, TimeSpan.FromSeconds(5), TimeSpan.FromSeconds(1), 1.0, false));
    }

    [Fact]
    public void RetryPolicy_Should_ThrowArgumentOutOfRangeException_WhenMultiplierLessThanOne()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() =>
            new RetryPolicy(3, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(10), 0.5, false));
    }
}
