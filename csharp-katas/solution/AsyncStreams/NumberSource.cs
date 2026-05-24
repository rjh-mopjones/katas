using System.Runtime.CompilerServices;

namespace Katas.AsyncStreams;

/// <summary>
/// Produces a sequence of integers as an async stream.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why async streams?</b> <c>IAsyncEnumerable&lt;T&gt;</c> allows a producer to yield items
/// one at a time, suspending between each yield, without buffering the entire sequence in memory.
/// This composes naturally with backpressure: the consumer controls when to pull the next item.
/// </para>
/// <para>
/// <b>Trade-offs vs. <c>Channel&lt;T&gt;</c>:</b> An async stream is pull-based and single-consumer
/// by design, making it simpler and allocation-cheaper. A channel is push-based, supports
/// multiple producers/consumers, and can be bounded. Use channels when you need fan-out or
/// producer/consumer decoupling; use async streams when you need a lazy, pull-based sequence.
/// </para>
/// <para>
/// <b>Cancellation:</b> <c>[EnumeratorCancellation]</c> wires the token passed to
/// <c>WithCancellation(ct)</c> into the method's <paramref name="ct"/> parameter automatically.
/// Without it the token would silently go nowhere.
/// </para>
/// </remarks>
public sealed class NumberSource
{
    /// <summary>
    /// Produces integers <c>0</c> through <c>count - 1</c> asynchronously, one per iteration.
    /// </summary>
    /// <param name="count">Number of items to produce. Must be non-negative.</param>
    /// <param name="ct">
    /// Cancellation token. Checked before each item is yielded so cancellation is responsive
    /// even when <c>Task.Yield()</c> resolves quickly (e.g. on a thread-pool thread).
    /// </param>
    /// <returns>A deferred async sequence; no work starts until the caller begins iterating.</returns>
    /// <exception cref="ArgumentOutOfRangeException">Thrown if <paramref name="count"/> is negative.</exception>
    public async IAsyncEnumerable<int> ProduceAsync(
        int count,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        if (count < 0) throw new ArgumentOutOfRangeException(nameof(count), "count must be non-negative.");

        for (int i = 0; i < count; i++)
        {
            ct.ThrowIfCancellationRequested();
            await Task.Yield();
            yield return i;
        }
    }
}
