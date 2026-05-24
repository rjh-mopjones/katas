using System.Threading.Channels;

namespace Katas.Channels;

/// <summary>
/// A bounded, single-producer-friendly async queue backed by a <see cref="Channel{T}"/>.
/// </summary>
/// <typeparam name="T">Item type.</typeparam>
/// <remarks>
/// <para>
/// <b>Why <see cref="Channel{T}"/> over <c>BlockingCollection&lt;T&gt;</c>?</b>
/// <c>Channel&lt;T&gt;</c> is natively async: writers and readers suspend with
/// <c>ValueTask</c>/<c>IAsyncEnumerable</c>, consuming no thread during the wait.
/// <c>BlockingCollection</c> blocks a thread-pool thread, which is wasteful for
/// high-concurrency async pipelines.
/// </para>
/// <para>
/// <b>Backpressure:</b> When the channel is full, <see cref="WriteAsync"/> returns
/// an incomplete <see cref="ValueTask"/> that resumes only when a reader drains a slot.
/// This naturally throttles producers without explicit semaphores or rate-limiters.
/// </para>
/// <para>
/// <b>Completion:</b> Calling <see cref="Complete"/> marks the writer side done.
/// The reader loop (<see cref="ReadAllAsync"/>) will drain remaining buffered items
/// and then terminate cleanly — no extra sentinel value required.
/// </para>
/// </remarks>
public sealed class BoundedQueue<T>
{
    private readonly Channel<T> _channel;

    /// <summary>
    /// Initialises a new queue with the given bounded <paramref name="capacity"/>.
    /// </summary>
    /// <param name="capacity">
    /// Maximum number of items buffered before writers are suspended. Must be positive.
    /// Choose a capacity that balances producer throughput with consumer lag tolerance.
    /// </param>
    public BoundedQueue(int capacity)
    {
        if (capacity < 1)
            throw new ArgumentOutOfRangeException(nameof(capacity), "Capacity must be positive.");

        _channel = Channel.CreateBounded<T>(new BoundedChannelOptions(capacity)
        {
            // Single-writer and single-reader optimisations are intentionally left at
            // their defaults (false) to keep the class usable for multi-producer scenarios.
            FullMode = BoundedChannelFullMode.Wait,
        });
    }

    /// <summary>
    /// Writes <paramref name="item"/> to the channel, waiting asynchronously if the channel is full.
    /// </summary>
    /// <param name="item">Item to enqueue.</param>
    /// <param name="ct">Cancellation token. Propagates <see cref="OperationCanceledException"/> if cancelled while waiting.</param>
    /// <returns>
    /// A <see cref="ValueTask"/> that completes when the item has been accepted by the channel.
    /// <see cref="ValueTask"/> is used here (instead of <see cref="Task"/>) because in the
    /// common non-full case the channel accepts the write synchronously, allowing the
    /// <see cref="ValueTask"/> to complete without a heap allocation.
    /// </returns>
    public ValueTask WriteAsync(T item, CancellationToken ct = default) =>
        _channel.Writer.WriteAsync(item, ct);

    /// <summary>
    /// Returns an async-enumerable that yields all items written to the channel until
    /// <see cref="Complete"/> is called and the buffer is drained.
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>A lazy async sequence of items.</returns>
    public IAsyncEnumerable<T> ReadAllAsync(CancellationToken ct = default) =>
        _channel.Reader.ReadAllAsync(ct);

    /// <summary>
    /// Signals that no more items will be written.  The read loop will terminate after
    /// draining any buffered items.
    /// </summary>
    public void Complete() => _channel.Writer.Complete();
}
