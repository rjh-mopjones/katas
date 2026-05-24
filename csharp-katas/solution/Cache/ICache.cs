using System.Diagnostics.CodeAnalysis;

namespace Katas.Cache;

/// <summary>
/// Defines a bounded key→value cache.
/// </summary>
/// <typeparam name="TKey">Key type.  Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
public interface ICache<TKey, TValue> where TKey : notnull
{
    /// <summary>
    /// Attempts to retrieve the value associated with <paramref name="key"/>.
    /// </summary>
    /// <param name="key">The lookup key.</param>
    /// <param name="value">
    /// When this method returns <see langword="true"/>, the cached value;
    /// <see langword="default"/> otherwise.
    /// </param>
    /// <returns><see langword="true"/> if the key exists in the cache.</returns>
    bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value);

    /// <summary>
    /// Inserts or updates <paramref name="key"/> with <paramref name="value"/>, evicting
    /// the least-recently/least-frequently used entry if the cache is at capacity.
    /// </summary>
    /// <param name="key">The key to insert or update.</param>
    /// <param name="value">The value to associate with <paramref name="key"/>.</param>
    void Put(TKey key, TValue value);

    /// <summary>Gets the number of entries currently held in the cache.</summary>
    int Count { get; }
}
