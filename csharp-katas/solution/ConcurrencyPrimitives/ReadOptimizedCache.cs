using System.Diagnostics.CodeAnalysis;

namespace Katas.ConcurrencyPrimitives;

/// <summary>
/// A thread-safe key→value cache optimised for read-heavy workloads via
/// <see cref="ReaderWriterLockSlim"/>, which allows unlimited concurrent readers
/// but exclusive writers.
/// </summary>
/// <typeparam name="TKey">Key type.  Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
/// <remarks>
/// <para>
/// <b>ReaderWriterLockSlim vs plain <c>lock</c>:</b>  A regular <c>lock</c> serialises all
/// readers even though concurrent reads are data-race-free.  <see cref="ReaderWriterLockSlim"/>
/// distinguishes three modes:
/// <list type="bullet">
///   <item><description><em>Read</em> — shared; any number of threads may hold it simultaneously.</description></item>
///   <item><description><em>UpgradeableRead</em> — shared with readers, but only one may be held at a time; can be atomically promoted to Write.</description></item>
///   <item><description><em>Write</em> — exclusive; no other reader or writer may proceed.</description></item>
/// </list>
/// For a cache where reads vastly outnumber writes this yields substantially higher throughput.
/// Use a plain <c>lock</c> when write contention is comparable to read contention, or when
/// simplicity is preferred over peak throughput.
/// </para>
/// <para>
/// <b>UpgradeableRead in <see cref="GetOrAdd"/>:</b>  We enter an upgradeable-read lock to
/// check whether the key already exists.  If it does, no write lock is needed and other readers
/// can proceed concurrently.  Only when the key is absent do we upgrade to a write lock to
/// insert the new value.  The upgrade is atomic with respect to other writers, preventing the
/// double-creation race that would occur if we released the read lock and then re-acquired a
/// write lock.
/// </para>
/// <para>
/// <b>Why not <c>ConcurrentDictionary.GetOrAdd</c>?</b>  <c>ConcurrentDictionary.GetOrAdd</c>
/// may invoke the factory under a fine-grained lock <em>but</em> the result of two concurrent
/// factories for the same key is non-deterministic (one wins, one is discarded).  Our
/// implementation guarantees the factory is called exactly once per key.
/// </para>
/// </remarks>
public sealed class ReadOptimizedCache<TKey, TValue> where TKey : notnull
{
    private readonly Dictionary<TKey, TValue> _store = new();
    private readonly ReaderWriterLockSlim _rwl = new(LockRecursionPolicy.NoRecursion);

    /// <summary>
    /// Returns the cached value for <paramref name="key"/>, invoking <paramref name="factory"/>
    /// to populate the cache on the first call for that key.
    /// </summary>
    /// <param name="key">Cache key.</param>
    /// <param name="factory">Called at most once per key, under a write lock.</param>
    /// <returns>The cached value.</returns>
    public TValue GetOrAdd(TKey key, Func<TKey, TValue> factory)
    {
        // Fast path: enter an upgradeable-read lock.  Other readers may proceed concurrently;
        // only other UpgradeableRead or Write attempts are serialised with this.
        _rwl.EnterUpgradeableReadLock();
        try
        {
            if (_store.TryGetValue(key, out TValue? existing))
                return existing;

            // Key absent — upgrade to a write lock atomically (no window for other writers).
            _rwl.EnterWriteLock();
            try
            {
                // Double-check: another UpgradeableRead may have inserted while we were waiting
                // for the write lock (not possible with NoRecursion and one UpgradeableRead at
                // a time, but defensive coding is cheap here).
                if (_store.TryGetValue(key, out existing))
                    return existing;

                TValue value = factory(key);
                _store[key] = value;
                return value;
            }
            finally
            {
                _rwl.ExitWriteLock();
            }
        }
        finally
        {
            _rwl.ExitUpgradeableReadLock();
        }
    }

    /// <summary>
    /// Attempts to retrieve the cached value for <paramref name="key"/> without invoking
    /// any factory.
    /// </summary>
    /// <param name="key">Cache key.</param>
    /// <param name="value">
    /// When this method returns <see langword="true"/>, the cached value; otherwise
    /// <see langword="default"/>.
    /// </param>
    /// <returns><see langword="true"/> if the key exists in the cache.</returns>
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value)
    {
        _rwl.EnterReadLock();
        try
        {
            return _store.TryGetValue(key, out value);
        }
        finally
        {
            _rwl.ExitReadLock();
        }
    }

    /// <summary>Gets the number of entries currently in the cache.</summary>
    public int Count
    {
        get
        {
            _rwl.EnterReadLock();
            try { return _store.Count; }
            finally { _rwl.ExitReadLock(); }
        }
    }
}
