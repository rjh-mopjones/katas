namespace Katas.CircuitBreaker;

/// <summary>
/// A classic circuit-breaker that protects a dependency by failing fast when it is
/// repeatedly unavailable, then cautiously probing for recovery.
/// </summary>
public sealed class CircuitBreaker
{
    /// <summary>Raised after each state transition, outside the lock.</summary>
    public event EventHandler<CircuitState>? StateChanged;

    /// <summary>Gets the current circuit state.</summary>
    public CircuitState State => throw new NotImplementedException();

    /// <summary>
    /// Initialises a new circuit breaker.
    /// </summary>
    /// <param name="failureThreshold">Consecutive failures required to trip to Open. Must be ≥ 1.</param>
    /// <param name="openDuration">How long the circuit stays Open before transitioning to HalfOpen. Must be positive.</param>
    /// <param name="halfOpenSuccessesToClose">Consecutive successes in HalfOpen required to close. Must be ≥ 1.</param>
    /// <param name="timeProvider">Clock abstraction. Defaults to <see cref="TimeProvider.System"/>.</param>
    public CircuitBreaker(
        int failureThreshold,
        TimeSpan openDuration,
        int halfOpenSuccessesToClose,
        TimeProvider? timeProvider = null) => throw new NotImplementedException();

    /// <summary>
    /// Executes the supplied action through the circuit breaker.
    /// Throws <see cref="CircuitOpenException"/> immediately when the circuit is Open.
    /// </summary>
    public Task<T> ExecuteAsync<T>(Func<CancellationToken, Task<T>> action, CancellationToken ct = default)
        => throw new NotImplementedException();
}
