namespace Katas.ConcurrencyPrimitives;

/// <summary>
/// A bounded, lazily-populated object pool that limits concurrent outstanding borrows to
/// <c>maxSize</c> via a <see cref="SemaphoreSlim"/>, and reuses idle instances via a
/// <see cref="ConcurrentQueue{T}"/>.
/// </summary>
/// <typeparam name="T">The pooled resource type.  Must be a non-null reference or value type.</typeparam>
/// <remarks>
/// <para>
/// <b>Why SemaphoreSlim models free permits:</b>  A <see cref="SemaphoreSlim"/> is initialised
/// with <c>initialCount = maxSize</c>, meaning there are <c>maxSize</c> "permits" available.
/// Each <see cref="AcquireAsync"/> call consumes one permit (<c>WaitAsync</c>); each
/// <see cref="Release"/> returns one permit (<c>Release()</c>).  If all permits are held by
/// callers, the next <c>WaitAsync</c> blocks asynchronously (no thread is wasted) until a
/// permit is returned.  This is exactly the <em>counting semaphore</em> model from Dijkstra —
/// the semaphore value equals the number of resources currently free.
/// </para>
/// <para>
/// <b>Why ConcurrentQueue for idle instances:</b>  Multiple threads may release objects
/// simultaneously.  <see cref="ConcurrentQueue{T}"/> provides wait-free enqueue/dequeue for
/// FIFO recycling without a <c>lock</c>.
/// </para>
/// <para>
/// <b>Lazy creation:</b>  Objects are created by the <paramref name="factory"/> only on first
/// demand when the idle queue is empty.  The total number of live instances is bounded by
/// <c>maxSize</c> (enforced by the semaphore), so <paramref name="factory"/> is called at most
/// <c>maxSize</c> times.
/// </para>
/// <para>
/// <b>Trade-offs:</b>  This pool does not shrink — once an object is created it lives until
/// the pool is GC'd.  For expensive resources (DB connections) that is usually desirable.
/// If you need eviction, add a TTL and a background scavenger.
/// ReaderWriterLockSlim is not needed here because the semaphore already sequences concurrent
/// acquires and <see cref="ConcurrentQueue{T}"/> is safe for concurrent producers/consumers.
/// </para>
/// </remarks>
public sealed class ResourcePool<T> where T : notnull
{
    private readonly Func<T> _factory;
    private readonly SemaphoreSlim _permits;
    private readonly ConcurrentQueue<T> _idle = new();

    /// <summary>
    /// Initialises the pool.
    /// </summary>
    /// <param name="factory">Called to create a new instance when the idle queue is empty.</param>
    /// <param name="maxSize">Maximum number of instances that may be borrowed simultaneously.</param>
    /// <exception cref="ArgumentNullException"><paramref name="factory"/> is <see langword="null"/>.</exception>
    /// <exception cref="ArgumentOutOfRangeException"><paramref name="maxSize"/> is less than 1.</exception>
    public ResourcePool(Func<T> factory, int maxSize)
    {
        if (factory is null) throw new ArgumentNullException(nameof(factory));
        if (maxSize < 1) throw new ArgumentOutOfRangeException(nameof(maxSize), maxSize, "maxSize must be at least 1.");
        _factory = factory;
        _permits = new SemaphoreSlim(maxSize, maxSize);
    }

    /// <summary>
    /// Acquires a resource from the pool, waiting asynchronously if all <c>maxSize</c> instances
    /// are currently borrowed.
    /// </summary>
    /// <param name="ct">Cancellation token.  Throws <see cref="OperationCanceledException"/> if cancelled.</param>
    /// <returns>A resource instance.  Caller MUST call <see cref="Release"/> when done.</returns>
    public async Task<T> AcquireAsync(CancellationToken ct = default)
    {
        // Consume one free permit.  Blocks asynchronously if none are available.
        await _permits.WaitAsync(ct).ConfigureAwait(false);

        // Reuse an idle instance if one is available; otherwise create a new one.
        if (!_idle.TryDequeue(out T? item))
            item = _factory();

        return item;
    }

    /// <summary>
    /// Returns a previously acquired resource to the pool.
    /// </summary>
    /// <param name="item">The resource to return.  Must have been obtained via <see cref="AcquireAsync"/>.</param>
    public void Release(T item)
    {
        _idle.Enqueue(item);
        _permits.Release(); // Restore the permit, potentially waking a waiting AcquireAsync.
    }

    /// <summary>
    /// Gets the number of idle (immediately acquirable) resources currently sitting in the pool.
    /// </summary>
    /// <remarks>
    /// This is a point-in-time snapshot.  The value may change before the caller acts on it.
    /// The semaphore's <see cref="SemaphoreSlim.CurrentCount"/> tracks the same quantity via
    /// the permit model, but reading both atomically is not guaranteed.
    /// </remarks>
    public int Available => _permits.CurrentCount;
}
