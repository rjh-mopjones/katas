namespace Katas.ScatterGather;

/// <summary>
/// Scatter/gather helpers for concurrent async fan-out.
/// </summary>
public static class Scatter
{
    /// <summary>
    /// Starts all tasks concurrently and awaits all of them, returning results in the same order as the input sequence.
    /// </summary>
    /// <typeparam name="T">Result type of each task.</typeparam>
    /// <param name="tasks">Factories that create the tasks; each receives the cancellation token.</param>
    /// <param name="ct">Cancellation token passed to each factory.</param>
    /// <returns>Results in the original input order.</returns>
    public static Task<IReadOnlyList<T>> GatherAllAsync<T>(
        IEnumerable<Func<CancellationToken, Task<T>>> tasks,
        CancellationToken ct = default) =>
        throw new NotImplementedException();

    /// <summary>
    /// Starts all tasks concurrently and returns results of those that complete successfully within <paramref name="timeout"/>.
    /// Slow or faulted tasks are omitted; results are in original input order.
    /// </summary>
    /// <typeparam name="T">Result type of each task.</typeparam>
    /// <param name="tasks">Task factories.</param>
    /// <param name="timeout">Maximum time to wait for each individual task.</param>
    /// <param name="timeProvider">Time provider used by <c>Task.WaitAsync</c> for deterministic testing.</param>
    /// <param name="ct">Cancellation token passed to each factory.</param>
    public static Task<IReadOnlyList<T>> GatherBeforeTimeoutAsync<T>(
        IEnumerable<Func<CancellationToken, Task<T>>> tasks,
        TimeSpan timeout,
        TimeProvider timeProvider,
        CancellationToken ct = default) =>
        throw new NotImplementedException();
}
