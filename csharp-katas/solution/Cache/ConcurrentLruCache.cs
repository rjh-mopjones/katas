using System.Diagnostics.CodeAnalysis;

namespace Katas.Cache;

/// <summary>
/// A thread-safe LRU cache that wraps <see cref="LruCache{TKey,TValue}"/> behind a single
/// coarse-grained <c>lock</c>.
/// </summary>
/// <typeparam name="TKey">Key type.  Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
/// <remarks>
/// <para>
/// <b>Why a plain <c>lock</c> instead of <see cref="System.Threading.ReaderWriterLockSlim"/>?</b>
/// At first glance, a cache looks like a read-heavy workload where concurrent reads should be
/// allowed.  However, every LRU <c>get</c> — even a pure "cache hit" — must <em>mutate</em>
/// the recency list by promoting the accessed node to the head of the linked list.  There is no
/// read path that leaves the data structure unchanged.  Because every operation (read or write)
/// is a write to the list, a <c>ReaderWriterLockSlim</c> would grant no concurrency advantage:
/// every caller would immediately escalate to a write lock, producing the same serialisation as
/// a plain <c>lock</c> — but with additional overhead from the rwlock machinery.
/// </para>
/// <para>
/// A plain <c>lock</c> is therefore the correct and simpler choice here.  If read throughput
/// were the bottleneck one could maintain a secondary read-only snapshot (e.g. a
/// <c>ImmutableDictionary</c>) rebuilt on write, trading higher write cost for lock-free reads
/// — but that is significant added complexity for most use cases.
/// </para>
/// <para>
/// <b>Alternative — ConcurrentDictionary with a Clock or CLOCK-Pro eviction:</b>  An
/// approximate LRU using a concurrent dictionary plus a clock hand avoids the per-access
/// mutex entirely but requires accepting some inaccuracy in eviction order.  Libraries like
/// Microsoft.Extensions.Caching.Memory use this approach for high-throughput scenarios.
/// </para>
/// </remarks>
public sealed class ConcurrentLruCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    private readonly LruCache<TKey, TValue> _inner;
    private readonly object _sync = new();

    /// <summary>
    /// Initialises the cache.
    /// </summary>
    /// <param name="capacity">Maximum number of entries.  Must be at least 1.</param>
    /// <exception cref="ArgumentOutOfRangeException"><paramref name="capacity"/> is less than 1.</exception>
    public ConcurrentLruCache(int capacity) => _inner = new LruCache<TKey, TValue>(capacity);

    /// <inheritdoc/>
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value)
    {
        lock (_sync)
            return _inner.TryGet(key, out value);
    }

    /// <inheritdoc/>
    public void Put(TKey key, TValue value)
    {
        lock (_sync)
            _inner.Put(key, value);
    }

    /// <inheritdoc/>
    public int Count
    {
        get { lock (_sync) return _inner.Count; }
    }
}
