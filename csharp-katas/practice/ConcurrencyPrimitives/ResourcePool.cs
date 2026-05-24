namespace Katas.ConcurrencyPrimitives;

/// <summary>
/// A bounded, lazily-populated object pool that limits concurrent outstanding borrows to
/// <c>maxSize</c> via a <see cref="SemaphoreSlim"/>, and reuses idle instances via a
/// <see cref="System.Collections.Concurrent.ConcurrentQueue{T}"/>.
/// </summary>
/// <typeparam name="T">The pooled resource type. Must be a non-null reference or value type.</typeparam>
public sealed class ResourcePool<T> where T : notnull
{
    /// <summary>
    /// Initialises the pool.
    /// </summary>
    /// <param name="factory">Called to create a new instance when the idle queue is empty.</param>
    /// <param name="maxSize">Maximum number of instances that may be borrowed simultaneously.</param>
    public ResourcePool(Func<T> factory, int maxSize) => throw new NotImplementedException();

    /// <summary>
    /// Acquires a resource from the pool, waiting asynchronously if all instances are currently borrowed.
    /// </summary>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>A resource instance. Caller MUST call <see cref="Release"/> when done.</returns>
    public Task<T> AcquireAsync(CancellationToken ct = default) => throw new NotImplementedException();

    /// <summary>
    /// Returns a previously acquired resource to the pool.
    /// </summary>
    /// <param name="item">The resource to return.</param>
    public void Release(T item) => throw new NotImplementedException();

    /// <summary>Gets the number of idle (immediately acquirable) resources currently in the pool.</summary>
    public int Available => throw new NotImplementedException();
}
