using System.Runtime.CompilerServices;

namespace Katas.AsyncStreams;

/// <summary>
/// Deferred, lazy LINQ-style operators for <see cref="IAsyncEnumerable{T}"/>.
/// </summary>
/// <remarks>
/// <para>
/// <b>Deferred execution:</b> Every method is an <c>async IAsyncEnumerable</c> — the returned
/// enumerable does no work until the caller starts iterating. This mirrors LINQ's deferred
/// semantics and means pipelines can be composed cheaply before any I/O runs.
/// </para>
/// <para>
/// <b>WithCancellation:</b> All inner <c>await foreach</c> loops use
/// <c>.WithCancellation(ct)</c> to forward the consumer's cancellation token into the
/// upstream source. Without this, cancelling the downstream iteration would not cancel the
/// upstream producer.
/// </para>
/// <para>
/// <b>ConfigureAwait:</b> In library code (no synchronisation context) we omit
/// <c>ConfigureAwait(false)</c> on the <c>await foreach</c> because the cancellation
/// forwarding is already handled via <c>WithCancellation</c>. Adding
/// <c>.ConfigureAwait(false)</c> on the enumerable itself is also valid but not required here.
/// </para>
/// </remarks>
public static class AsyncSequenceOperators
{
    /// <summary>
    /// Projects each element of an async sequence using <paramref name="selector"/>.
    /// </summary>
    /// <typeparam name="T">Source element type.</typeparam>
    /// <typeparam name="TResult">Projected element type.</typeparam>
    /// <param name="source">The upstream async sequence.</param>
    /// <param name="selector">Synchronous projection applied to each element.</param>
    /// <param name="ct">Cancellation token forwarded to the upstream iteration.</param>
    /// <returns>A deferred async sequence of projected elements.</returns>
    public static async IAsyncEnumerable<TResult> SelectAsync<T, TResult>(
        this IAsyncEnumerable<T> source,
        Func<T, TResult> selector,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        // Honour an already-cancelled (or mid-stream cancelled) token regardless of whether the
        // upstream source observes it — operators must be cancellation-correct on their own.
        ct.ThrowIfCancellationRequested();
        await foreach (T item in source.WithCancellation(ct))
        {
            ct.ThrowIfCancellationRequested();
            yield return selector(item);
        }
    }

    /// <summary>
    /// Filters an async sequence to only elements that satisfy <paramref name="predicate"/>.
    /// </summary>
    /// <typeparam name="T">Element type.</typeparam>
    /// <param name="source">The upstream async sequence.</param>
    /// <param name="predicate">Synchronous filter applied to each element.</param>
    /// <param name="ct">Cancellation token forwarded to the upstream iteration.</param>
    /// <returns>A deferred async sequence of matching elements.</returns>
    public static async IAsyncEnumerable<T> WhereAsync<T>(
        this IAsyncEnumerable<T> source,
        Func<T, bool> predicate,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        ct.ThrowIfCancellationRequested();
        await foreach (T item in source.WithCancellation(ct))
        {
            ct.ThrowIfCancellationRequested();
            if (predicate(item))
                yield return item;
        }
    }

    /// <summary>
    /// Returns at most the first <paramref name="count"/> elements from an async sequence.
    /// </summary>
    /// <typeparam name="T">Element type.</typeparam>
    /// <param name="source">The upstream async sequence.</param>
    /// <param name="count">Maximum number of elements to yield. If zero, the sequence is empty.</param>
    /// <param name="ct">Cancellation token forwarded to the upstream iteration.</param>
    /// <returns>A deferred async sequence of up to <paramref name="count"/> elements.</returns>
    /// <remarks>
    /// <b>Early termination:</b> Once <paramref name="count"/> items have been yielded the
    /// iterator returns without pulling further items from <paramref name="source"/>, which
    /// may leave the upstream producer uncancelled. For producers that hold external resources
    /// the caller should additionally pass a <see cref="CancellationToken"/> or the upstream
    /// should implement <c>IAsyncDisposable</c> cleanup.
    /// </remarks>
    public static async IAsyncEnumerable<T> TakeAsync<T>(
        this IAsyncEnumerable<T> source,
        int count,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        int taken = 0;
        await foreach (T item in source.WithCancellation(ct))
        {
            if (taken >= count) yield break;
            yield return item;
            taken++;
        }
    }
}
