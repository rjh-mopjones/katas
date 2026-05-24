namespace Katas.RateLimit;

/// <summary>
/// Token-bucket rate limiter: tokens accumulate at a fixed rate up to a capacity ceiling.
/// </summary>
public sealed class TokenBucketRateLimiter : IRateLimiter
{
    /// <summary>
    /// Initialises a new token-bucket limiter.
    /// </summary>
    /// <param name="capacity">Maximum number of tokens the bucket can hold (also the initial fill level).</param>
    /// <param name="refillTokensPerSecond">Rate at which tokens are added per second. Must be positive.</param>
    /// <param name="timeProvider">Clock abstraction. Defaults to <see cref="TimeProvider.System"/>.</param>
    public TokenBucketRateLimiter(
        long capacity,
        double refillTokensPerSecond,
        TimeProvider? timeProvider = null) => throw new NotImplementedException();

    /// <summary>
    /// Attempts to acquire <paramref name="permits"/> tokens, lazily refilling first.
    /// </summary>
    public bool TryAcquire(int permits = 1) => throw new NotImplementedException();
}
