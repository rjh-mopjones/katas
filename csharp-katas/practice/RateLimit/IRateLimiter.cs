namespace Katas.RateLimit;

/// <summary>
/// Defines a rate limiter that controls how many permits (tokens) can be consumed per unit of time.
/// </summary>
public interface IRateLimiter
{
    /// <summary>
    /// Attempts to acquire <paramref name="permits"/> tokens from the limiter without blocking.
    /// </summary>
    /// <param name="permits">Number of tokens to consume. Must be at least 1.</param>
    /// <returns>
    /// <see langword="true"/> if the permits were granted and the caller may proceed;
    /// <see langword="false"/> if the rate limit has been exceeded.
    /// </returns>
    bool TryAcquire(int permits = 1);
}
