using System.Diagnostics.CodeAnalysis;

namespace Katas.ConcurrencyPrimitives;

/// <summary>
/// A thread-safe key→value cache optimised for read-heavy workloads via
/// <see cref="ReaderWriterLockSlim"/>, which allows unlimited concurrent readers
/// but exclusive writers.
/// </summary>
/// <typeparam name="TKey">Key type. Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
public sealed class ReadOptimizedCache<TKey, TValue> where TKey : notnull
{
    /// <summary>
    /// Returns the cached value for <paramref name="key"/>, invoking <paramref name="factory"/>
    /// to populate the cache on the first call for that key.
    /// </summary>
    /// <param name="key">Cache key.</param>
    /// <param name="factory">Called at most once per key, under a write lock.</param>
    /// <returns>The cached value.</returns>
    public TValue GetOrAdd(TKey key, Func<TKey, TValue> factory) => throw new NotImplementedException();

    /// <summary>
    /// Attempts to retrieve the cached value for <paramref name="key"/> without invoking any factory.
    /// </summary>
    /// <param name="key">Cache key.</param>
    /// <param name="value">The cached value if found; otherwise <see langword="default"/>.</param>
    /// <returns><see langword="true"/> if the key exists in the cache.</returns>
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value)
    {
        throw new NotImplementedException();
    }

    /// <summary>Gets the number of entries currently in the cache.</summary>
    public int Count => throw new NotImplementedException();
}
