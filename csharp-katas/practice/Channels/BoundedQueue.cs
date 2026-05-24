using System.Threading.Channels;

namespace Katas.Channels;

/// <summary>
/// A bounded, single-producer-friendly async queue backed by a <see cref="Channel{T}"/>.
/// </summary>
/// <typeparam name="T">Item type.</typeparam>
public sealed class BoundedQueue<T>
{
    /// <summary>
    /// Initialises a new queue with the given bounded <paramref name="capacity"/>.
    /// </summary>
    /// <param name="capacity">Maximum number of items buffered before writers are suspended. Must be positive.</param>
    public BoundedQueue(int capacity) => throw new NotImplementedException();

    /// <summary>
    /// Writes <paramref name="item"/> to the channel, waiting asynchronously if the channel is full.
    /// </summary>
    /// <param name="item">Item to enqueue.</param>
    /// <param name="ct">Cancellation token.</param>
    public ValueTask WriteAsync(T item, CancellationToken ct = default) =>
        throw new NotImplementedException();

    /// <summary>
    /// Returns an async-enumerable that yields all items until <see cref="Complete"/> is called and the buffer is drained.
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    public IAsyncEnumerable<T> ReadAllAsync(CancellationToken ct = default) =>
        throw new NotImplementedException();

    /// <summary>
    /// Signals that no more items will be written. The read loop terminates after draining buffered items.
    /// </summary>
    public void Complete() => throw new NotImplementedException();
}
