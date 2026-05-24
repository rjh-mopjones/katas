using System.Collections.Concurrent;

namespace Katas.Idempotency;

/// <summary>
/// Guarantees that an async action runs at most once per string key, even under concurrent
/// duplicate calls, returning the same cached result (or exception) for all callers.
/// </summary>
public sealed class IdempotentRunner
{
    /// <summary>
    /// Runs <paramref name="action"/> for the given <paramref name="key"/> exactly once,
    /// returning its result to all concurrent or subsequent callers with the same key.
    /// </summary>
    /// <typeparam name="T">The result type of the action.</typeparam>
    /// <param name="key">A string that identifies this logical operation.</param>
    /// <param name="action">The idempotent operation to execute at most once per unique key.</param>
    /// <param name="ct">Cancellation token forwarded to the action on first execution only.</param>
    /// <returns>A task resolving to the result of the first execution for <paramref name="key"/>.</returns>
    public Task<T> RunOnceAsync<T>(
        string key,
        Func<CancellationToken, Task<T>> action,
        CancellationToken ct = default) => throw new NotImplementedException();
}
