namespace Katas.CircuitBreaker;

/// <summary>
/// The possible states of a <see cref="CircuitBreaker"/>.
/// </summary>
/// <remarks>
/// State machine transitions:
/// <list type="bullet">
///   <item><description><see cref="Closed"/> → <see cref="Open"/>: consecutive failure count reaches the threshold.</description></item>
///   <item><description><see cref="Open"/> → <see cref="HalfOpen"/>: the open-duration cooldown expires.</description></item>
///   <item><description><see cref="HalfOpen"/> → <see cref="Closed"/>: the required number of consecutive successes is reached.</description></item>
///   <item><description><see cref="HalfOpen"/> → <see cref="Open"/>: any failure while in half-open resets the cooldown.</description></item>
/// </list>
/// </remarks>
public enum CircuitState
{
    /// <summary>Requests flow through normally. Failures are counted.</summary>
    Closed,

    /// <summary>Requests are rejected immediately without invoking the action.</summary>
    Open,

    /// <summary>
    /// A probe state: a limited number of requests are tried to determine whether the
    /// downstream dependency has recovered.
    /// </summary>
    HalfOpen,
}
