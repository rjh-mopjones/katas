using System.Diagnostics.CodeAnalysis;

namespace Katas.Cache;

/// <summary>
/// A Least-Frequently-Used (LFU) cache with O(1) <see cref="TryGet"/> and <see cref="Put"/>
/// operations, using the frequency-bucket + <c>minFreq</c> algorithm.
/// </summary>
/// <typeparam name="TKey">Key type. Must not be <see langword="null"/>.</typeparam>
/// <typeparam name="TValue">Value type.</typeparam>
public sealed class LfuCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    /// <summary>
    /// Initialises the cache.
    /// </summary>
    /// <param name="capacity">Maximum number of entries. Must be at least 1.</param>
    public LfuCache(int capacity) => throw new NotImplementedException();

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
