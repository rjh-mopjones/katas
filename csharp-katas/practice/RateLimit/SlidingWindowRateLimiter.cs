namespace Katas.RateLimit;

/// <summary>
/// Rate limiter using the sliding-window counter approximation.
/// </summary>
public sealed class SlidingWindowRateLimiter : IRateLimiter
{
    /// <summary>
    /// Initialises a sliding-window counter limiter.
    /// </summary>
    /// <param name="maxRequests">Maximum requests permitted in any sliding window. Must be ≥ 1.</param>
    /// <param name="window">The window duration. Must be positive.</param>
    /// <param name="timeProvider">Clock abstraction. Defaults to <see cref="TimeProvider.System"/>.</param>
    public SlidingWindowRateLimiter(
        int maxRequests,
        TimeSpan window,
        TimeProvider? timeProvider = null) => throw new NotImplementedException();

    /// <summary>
    /// Attempts to acquire <paramref name="permits"/>, blending previous and current window counts.
    /// </summary>
    public bool TryAcquire(int permits = 1) => throw new NotImplementedException();
}
