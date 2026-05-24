namespace Katas.Retry;

/// <summary>
/// Immutable configuration that drives exponential-backoff retry behaviour.
/// </summary>
/// <param name="MaxAttempts">Total number of attempts (including the first try). Must be at least 1.</param>
/// <param name="BaseDelay">Delay before the second attempt. Must be non-negative.</param>
/// <param name="MaxDelay">Upper bound on any single inter-attempt delay. Must be >= <see cref="BaseDelay"/>.</param>
/// <param name="Multiplier">Growth factor per attempt. Must be >= 1.0.</param>
/// <param name="UseJitter">When true, full-jitter is applied to the computed delay.</param>
public sealed record RetryPolicy(
    int MaxAttempts,
    TimeSpan BaseDelay,
    TimeSpan MaxDelay,
    double Multiplier,
    bool UseJitter)
{
    /// <summary>
    /// Computes the delay before attempt number <paramref name="attempt"/>.
    /// </summary>
    /// <param name="attempt">1-based attempt index (attempt 1 = first retry after initial failure).</param>
    /// <param name="jitterFraction">A value in [0,1) applied as a full-jitter multiplier when <see cref="UseJitter"/> is true.</param>
    public TimeSpan ComputeDelay(int attempt, double jitterFraction) =>
        throw new NotImplementedException();
}
