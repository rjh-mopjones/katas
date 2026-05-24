using System.Diagnostics.CodeAnalysis;

namespace Katas.Cache;

/// <summary>
/// A Least-Recently-Used (LRU) cache with O(1) <see cref="TryGet"/> and <see cref="Put"/>
/// operations, backed by a <see cref="Dictionary{TKey, TValue}"/> and a
/// <see cref="LinkedList{T}"/>.
/// </summary>
/// <typeparam name="TKey">Key type. Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
public sealed class LruCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    /// <summary>
    /// Initialises the cache.
    /// </summary>
    /// <param name="capacity">Maximum number of entries. Must be at least 1.</param>
    public LruCache(int capacity) => throw new NotImplementedException();

    /// <inheritdoc/>
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value)
    {
        throw new NotImplementedException();
    }

    /// <inheritdoc/>
    public void Put(TKey key, TValue value) => throw new NotImplementedException();

    /// <inheritdoc/>
    public int Count => throw new NotImplementedException();
}
