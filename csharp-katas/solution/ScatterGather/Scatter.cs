namespace Katas.ScatterGather;

/// <summary>
/// Scatter/gather helpers for concurrent async fan-out.
/// </summary>
/// <remarks>
/// <b>Scatter/Gather pattern:</b> scatter work across many independent async operations
/// (fan-out), then gather the results once they complete (fan-in). This maximises throughput
/// for I/O-bound workloads compared to sequential execution.
/// </remarks>
public static class Scatter
{
    /// <summary>
    /// Starts all tasks concurrently and awaits all of them, returning results in the same
    /// order as the input sequence.
    /// </summary>
    /// <typeparam name="T">Result type of each task.</typeparam>
    /// <param name="tasks">
    /// Factories that create the tasks. Each factory receives the cancellation token so the
    /// individual operations can be cancelled.
    /// </param>
    /// <param name="ct">Cancellation token passed to each factory.</param>
    /// <returns>Results in the original input order.</returns>
    /// <exception cref="AggregateException">
    /// If one or more tasks fault, <c>Task.WhenAll</c> wraps all exceptions in an
    /// <see cref="AggregateException"/>. The <c>await</c> unwraps to the first inner exception,
    /// but the remaining exceptions are available via <c>ex.InnerExceptions</c>.
    /// </exception>
    /// <remarks>
    /// <para>
    /// <b>allOf-collect gotcha:</b> Results are read from the individual <see cref="Task{T}"/>
    /// objects via <c>started[i].Result</c> rather than from <c>Task.WhenAll</c>'s return value.
    /// When the input is an <c>IEnumerable</c> (not an array), <c>WhenAll</c> internally
    /// enumerates it to an array, but that array is of the <c>Task&lt;T&gt;</c> objects, not
    /// their results. Using <c>WhenAll(...).Result</c> is equivalent but relies on the same
    /// internal enumeration — reading from the individual tasks is more transparent and avoids
    /// any confusion if the overload resolution changes.
    /// </para>
    /// <para>
    /// <b>Order guarantee:</b> Because we index into <c>started</c> (same order as the
    /// factory enumeration), the returned list always matches the input order regardless of
    /// completion order.
    /// </para>
    /// </remarks>
    public static async Task<IReadOnlyList<T>> GatherAllAsync<T>(
        IEnumerable<Func<CancellationToken, Task<T>>> tasks,
        CancellationToken ct = default)
    {
        // Materialise to an array so we can index and count without re-enumerating.
        Task<T>[] started = tasks.Select(f => f(ct)).ToArray();

        await Task.WhenAll(started).ConfigureAwait(false);

        // Results come from the individual tasks (in input order), not from WhenAll's return.
        T[] results = new T[started.Length];
        for (int i = 0; i < started.Length; i++)
            results[i] = started[i].Result;

        return results;
    }

    /// <summary>
    /// Starts all tasks concurrently and returns the results of those that complete
    /// successfully within <paramref name="timeout"/>.
    /// </summary>
    /// <typeparam name="T">Result type of each task.</typeparam>
    /// <param name="tasks">Task factories.</param>
    /// <param name="timeout">Maximum time to wait for each individual task.</param>
    /// <param name="timeProvider">
    /// Time provider used by <c>Task.WaitAsync</c> so tests can substitute
    /// <c>FakeTimeProvider</c> for deterministic timeout control.
    /// </param>
    /// <param name="ct">Cancellation token passed to each factory and to the linked CTS.</param>
    /// <returns>
    /// Results of tasks that completed successfully within <paramref name="timeout"/>,
    /// in the same order as the input sequence (slow/faulted tasks are omitted).
    /// </returns>
    /// <remarks>
    /// <para>
    /// <b>Linked CTS:</b> A <see cref="CancellationTokenSource"/> is linked to
    /// <paramref name="ct"/> and cancelled after gathering completes. This signals any
    /// still-running tasks that their results are no longer needed, allowing them to stop
    /// early if they observe cancellation. Without this, "loser" tasks would continue
    /// consuming resources silently in the background.
    /// </para>
    /// <para>
    /// <b>Why <c>Task.WaitAsync</c> per-task?</b> Using a single <c>Task.WhenAll</c> with
    /// a global timeout would fail as soon as any task times out. Per-task
    /// <c>WaitAsync</c> lets us collect all fast completions independently.
    /// </para>
    /// <para>
    /// <b>Faulted tasks:</b> Tasks that throw (for any reason including timeout or genuine
    /// fault) are silently dropped. If distinguishing between timeout and fault is important,
    /// the catch block can inspect the exception type and handle accordingly.
    /// </para>
    /// </remarks>
    public static async Task<IReadOnlyList<T>> GatherBeforeTimeoutAsync<T>(
        IEnumerable<Func<CancellationToken, Task<T>>> tasks,
        TimeSpan timeout,
        TimeProvider timeProvider,
        CancellationToken ct = default)
    {
        using var linkedCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        CancellationToken linkedToken = linkedCts.Token;

        // Start all tasks with the linked token so they can be cancelled when done.
        Task<T>[] started = tasks.Select(f => f(linkedToken)).ToArray();

        // Race each started task against the timeout.
        Task<(bool Ok, T Value, int Index)>[] races = started
            .Select((t, i) => RaceWithTimeout(t, i, timeout, timeProvider))
            .ToArray();

        (bool Ok, T Value, int Index)[] outcomes = await Task.WhenAll(races).ConfigureAwait(false);

        // Cancel losers now that gathering is complete.
        await linkedCts.CancelAsync().ConfigureAwait(false);

        // Preserve input order: collect only successful outcomes, sorted by original index.
        List<T> results = outcomes
            .Where(o => o.Ok)
            .OrderBy(o => o.Index)
            .Select(o => o.Value)
            .ToList();

        return results;
    }

    private static async Task<(bool Ok, T Value, int Index)> RaceWithTimeout<T>(
        Task<T> task, int index, TimeSpan timeout, TimeProvider timeProvider)
    {
        try
        {
            T value = await task.WaitAsync(timeout, timeProvider).ConfigureAwait(false);
            return (true, value, index);
        }
        catch
        {
            return (false, default!, index);
        }
    }
}
