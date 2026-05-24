namespace Katas.Retry;

/// <summary>
/// Executes an async operation, retrying on failure according to a <see cref="RetryPolicy"/>.
/// </summary>
public sealed class Retrier
{
    /// <summary>
    /// Initialises a new <see cref="Retrier"/>.
    /// </summary>
    /// <param name="policy">Retry configuration.</param>
    /// <param name="timeProvider">Time provider used for <c>Task.Delay</c>. Defaults to <see cref="TimeProvider.System"/>.</param>
    /// <param name="jitterSource">Returns a value in [0,1) used as the jitter fraction. Defaults to <c>Random.Shared.NextDouble</c>.</param>
    public Retrier(
        RetryPolicy policy,
        TimeProvider? timeProvider = null,
        Func<double>? jitterSource = null) =>
        throw new NotImplementedException();

    /// <summary>
    /// Executes <paramref name="action"/>, retrying on exception up to <see cref="RetryPolicy.MaxAttempts"/> times total.
    /// </summary>
    /// <typeparam name="T">Return type of the action.</typeparam>
    /// <param name="action">The operation to execute. Receives the <see cref="CancellationToken"/>.</param>
    /// <param name="ct">Cancellation token. Genuine cancellations are not retried.</param>
    /// <returns>The return value of the first successful invocation.</returns>
    /// <exception cref="Exception">The exception thrown by the final attempt once all attempts are exhausted.</exception>
    public Task<T> ExecuteAsync<T>(
        Func<CancellationToken, Task<T>> action,
        CancellationToken ct = default) =>
        throw new NotImplementedException();
}
