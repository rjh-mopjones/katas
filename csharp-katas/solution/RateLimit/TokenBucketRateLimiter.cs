namespace Katas.RateLimit;

/// <summary>
/// Token-bucket rate limiter: tokens accumulate at a fixed rate up to a capacity ceiling.
/// </summary>
/// <remarks>
/// <para>
/// <b>Algorithm:</b> The bucket holds at most <c>capacity</c> tokens. On every call to
/// <see cref="TryAcquire"/> the implementation first performs a <em>lazy refill</em>: it
/// computes how much time has elapsed since the last refill, multiplies by
/// <c>refillTokensPerSecond</c>, adds the result to the current token count (clamped to
/// <c>capacity</c>), and records the new timestamp.  If enough tokens are now available the
/// call succeeds and the tokens are deducted; otherwise it fails immediately (no waiting).
/// </para>
/// <para>
/// <b>Why lazy refill?</b>  A background timer that tops up the bucket every tick requires an
/// extra thread/task and risks timer drift accumulating. Lazy refill defers all arithmetic to
/// the point of use, is simpler, and is equally accurate.
/// </para>
/// <para>
/// <b>Thread-safety — <c>lock</c> vs. CAS:</b>  This implementation uses a plain <c>lock</c>
/// because the critical section is tiny (a few arithmetic ops plus two field writes) and
/// contention is expected to be low on typical workloads.  A lock-free alternative would use
/// a compare-and-swap loop on a packed <c>long</c> (token count + refill timestamp encoded
/// together, read/written via <see cref="Interlocked.CompareExchange(ref long,long,long)"/>).
/// That avoids kernel transitions under high contention but complicates the encoding and makes
/// the code harder to audit; reach for it only when profiling shows lock contention is a
/// bottleneck.
/// </para>
/// <para>
/// <b>Burst behaviour:</b> Because tokens accumulate while the bucket is idle, a caller that
/// waits long enough can immediately consume up to <c>capacity</c> tokens — a controlled burst.
/// </para>
/// <para>
/// <b>TimeProvider:</b> All time measurements use <see cref="TimeProvider.GetTimestamp"/> /
/// <see cref="TimeProvider.GetElapsedTime"/> so that tests can run under a
/// <c>FakeTimeProvider</c> without real sleeps.
/// </para>
/// </remarks>
public sealed class TokenBucketRateLimiter : IRateLimiter
{
    private readonly long _capacity;
    private readonly double _refillTokensPerSecond;
    private readonly TimeProvider _timeProvider;
    private readonly object _lock = new();

    private double _tokens;
    private long _lastRefillTimestamp;

    /// <summary>
    /// Initialises a new token-bucket limiter.
    /// </summary>
    /// <param name="capacity">Maximum number of tokens the bucket can hold (also the initial fill level).</param>
    /// <param name="refillTokensPerSecond">Rate at which tokens are added per second. Must be positive.</param>
    /// <param name="timeProvider">
    /// Clock abstraction. Defaults to <see cref="TimeProvider.System"/>. Pass a
    /// <c>FakeTimeProvider</c> in tests.
    /// </param>
    /// <exception cref="ArgumentOutOfRangeException">
    /// Thrown when <paramref name="capacity"/> is less than 1 or
    /// <paramref name="refillTokensPerSecond"/> is not positive.
    /// </exception>
    public TokenBucketRateLimiter(
        long capacity,
        double refillTokensPerSecond,
        TimeProvider? timeProvider = null)
    {
        if (capacity < 1)
            throw new ArgumentOutOfRangeException(nameof(capacity), "Capacity must be at least 1.");
        if (refillTokensPerSecond <= 0)
            throw new ArgumentOutOfRangeException(nameof(refillTokensPerSecond), "Refill rate must be positive.");

        _capacity = capacity;
        _refillTokensPerSecond = refillTokensPerSecond;
        _timeProvider = timeProvider ?? TimeProvider.System;
        _tokens = capacity;
        _lastRefillTimestamp = _timeProvider.GetTimestamp();
    }

    /// <inheritdoc />
    /// <remarks>
    /// Requests for more permits than <c>capacity</c> are always rejected, even when the
    /// bucket is full, because they can never succeed.
    /// </remarks>
    public bool TryAcquire(int permits = 1)
    {
        if (permits < 1)
            throw new ArgumentOutOfRangeException(nameof(permits), "Permits must be at least 1.");

        // Fast path: reject immediately if the request can never be satisfied.
        if (permits > _capacity) return false;

        lock (_lock)
        {
            Refill();
            if (_tokens < permits) return false;
            _tokens -= permits;
            return true;
        }
    }

    /// <summary>
    /// Computes elapsed time since the last refill and adds the appropriate tokens.
    /// Must be called inside the lock.
    /// </summary>
    private void Refill()
    {
        long now = _timeProvider.GetTimestamp();
        TimeSpan elapsed = _timeProvider.GetElapsedTime(_lastRefillTimestamp, now);
        double newTokens = elapsed.TotalSeconds * _refillTokensPerSecond;
        _tokens = Math.Min(_capacity, _tokens + newTokens);
        _lastRefillTimestamp = now;
    }
}
