namespace Katas.RateLimit;

/// <summary>
/// Rate limiter using the <em>sliding-window counter</em> approximation.
/// </summary>
/// <remarks>
/// <para>
/// <b>Fixed window vs. sliding window:</b>  A pure fixed-window counter resets to zero at the
/// boundary of each window (e.g. every second).  This creates a "boundary spike": a caller
/// could send <c>maxRequests</c> requests at the end of window N and another <c>maxRequests</c>
/// at the start of window N+1, yielding 2 × <c>maxRequests</c> in a stretch shorter than the
/// configured window.  The sliding-window counter approximation eliminates most of that spike
/// by blending the previous window's count (weighted by how much of the current window remains
/// un-elapsed) with the current window's count.
/// </para>
/// <para>
/// <b>Algorithm:</b>
/// Let <c>prev</c> = request count in the previous fixed window, <c>curr</c> = count in the
/// current window, and <c>progress</c> = fraction of the current window that has elapsed
/// (0–1).  Estimated requests in the sliding window = <c>prev × (1 − progress) + curr</c>.
/// If this value + the requested permits is ≤ <c>maxRequests</c>, the request is accepted.
/// </para>
/// <para>
/// <b>Trade-offs:</b>  The approximation slightly under- or over-counts because it assumes the
/// previous window's traffic was uniformly distributed.  It uses O(1) memory (two counters +
/// a timestamp) rather than O(n) for a sliding-window log (which stores the exact timestamp of
/// every request).  For most production use-cases the approximation is accurate enough.
/// </para>
/// <para>
/// <b>Thread-safety:</b>  A <c>lock</c> guards all state.  The critical section is O(1) work
/// so contention impact is minimal.
/// </para>
/// </remarks>
public sealed class SlidingWindowRateLimiter : IRateLimiter
{
    private readonly int _maxRequests;
    private readonly TimeSpan _window;
    private readonly TimeProvider _timeProvider;
    private readonly object _lock = new();

    // Timestamp (in TimeProvider units) marking the start of the current fixed window.
    private long _windowStart;
    // Request counts for the previous and current fixed windows.
    private int _previousCount;
    private int _currentCount;

    /// <summary>
    /// Initialises a sliding-window counter limiter.
    /// </summary>
    /// <param name="maxRequests">Maximum requests permitted in any sliding window. Must be ≥ 1.</param>
    /// <param name="window">The window duration. Must be positive.</param>
    /// <param name="timeProvider">
    /// Clock abstraction. Defaults to <see cref="TimeProvider.System"/>. Pass a
    /// <c>FakeTimeProvider</c> in tests.
    /// </param>
    /// <exception cref="ArgumentOutOfRangeException">
    /// Thrown when <paramref name="maxRequests"/> is less than 1 or
    /// <paramref name="window"/> is not positive.
    /// </exception>
    public SlidingWindowRateLimiter(
        int maxRequests,
        TimeSpan window,
        TimeProvider? timeProvider = null)
    {
        if (maxRequests < 1)
            throw new ArgumentOutOfRangeException(nameof(maxRequests), "maxRequests must be at least 1.");
        if (window <= TimeSpan.Zero)
            throw new ArgumentOutOfRangeException(nameof(window), "Window must be positive.");

        _maxRequests = maxRequests;
        _window = window;
        _timeProvider = timeProvider ?? TimeProvider.System;
        _windowStart = _timeProvider.GetTimestamp();
    }

    /// <inheritdoc />
    public bool TryAcquire(int permits = 1)
    {
        if (permits < 1)
            throw new ArgumentOutOfRangeException(nameof(permits), "Permits must be at least 1.");

        lock (_lock)
        {
            long now = _timeProvider.GetTimestamp();
            AdvanceWindow(now);

            // Fraction of the current window that has elapsed (0.0 – 1.0).
            TimeSpan elapsedInWindow = _timeProvider.GetElapsedTime(_windowStart, now);
            double progress = Math.Clamp(elapsedInWindow.TotalSeconds / _window.TotalSeconds, 0.0, 1.0);

            // Weighted estimate of requests within the trailing sliding window.
            double estimated = _previousCount * (1.0 - progress) + _currentCount;

            if (estimated + permits > _maxRequests) return false;

            _currentCount += permits;
            return true;
        }
    }

    /// <summary>
    /// Advances the fixed-window bookkeeping to the current timestamp if the window has rolled.
    /// Must be called while holding <c>_lock</c>.
    /// </summary>
    private void AdvanceWindow(long now)
    {
        TimeSpan elapsed = _timeProvider.GetElapsedTime(_windowStart, now);

        if (elapsed < _window) return; // Still in the same window.

        // How many complete windows have passed?
        long fullWindows = (long)(elapsed.TotalSeconds / _window.TotalSeconds);

        if (fullWindows >= 2)
        {
            // The previous window is outside the look-back range.
            _previousCount = 0;
        }
        else
        {
            // Exactly one window boundary crossed.
            _previousCount = _currentCount;
        }

        _currentCount = 0;

        // Advance the window start by the number of full windows elapsed.
        double windowSeconds = _window.TotalSeconds;
        double remainderSeconds = elapsed.TotalSeconds - fullWindows * windowSeconds;
        long remainderTicks = (long)(remainderSeconds * _timeProvider.TimestampFrequency);
        _windowStart = now - remainderTicks;
    }
}
