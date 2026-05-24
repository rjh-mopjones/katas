namespace Katas.CircuitBreaker;

/// <summary>
/// Thrown by <see cref="CircuitBreaker.ExecuteAsync{T}"/> when the circuit is
/// in the <see cref="CircuitState.Open"/> state and the request is fast-failed.
/// </summary>
public sealed class CircuitOpenException : Exception
{
    /// <summary>
    /// Initialises a new instance with the supplied message.
    /// </summary>
    /// <param name="message">A human-readable description of why the circuit is open.</param>
    public CircuitOpenException(string message) : base(message) { }
}
