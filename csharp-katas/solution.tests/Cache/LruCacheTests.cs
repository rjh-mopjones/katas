namespace Katas.Tests.Cache;

using Katas.Cache;

public sealed class LruCacheTests
{
    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    [Fact]
    public void TryGet_Should_ReturnFalseForMissingKey()
    {
        var cache = new LruCache<string, int>(capacity: 3);
        Assert.False(cache.TryGet("missing", out _));
    }

    [Fact]
    public void Put_TryGet_Should_StoreAndReturnValue()
    {
        var cache = new LruCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        bool found = cache.TryGet("a", out int value);
        Assert.True(found);
        Assert.Equal(1, value);
    }

    [Fact]
    public void Put_Should_UpdateExistingKey()
    {
        var cache = new LruCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        cache.Put("a", 99);
        cache.TryGet("a", out int value);
        Assert.Equal(99, value);
        Assert.Equal(1, cache.Count);
    }

    // -------------------------------------------------------------------------
    // Eviction order
    // -------------------------------------------------------------------------

    [Fact]
    public void Put_Should_EvictLeastRecentlyUsedOnOverflow()
    {
        var cache = new LruCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        cache.Put("b", 2);
        cache.Put("c", 3);
        // Access order now: c (most recent) → b → a (LRU)

        cache.Put("d", 4); // Should evict "a".

        Assert.False(cache.TryGet("a", out _));
        Assert.True(cache.TryGet("b", out _));
        Assert.True(cache.TryGet("c", out _));
        Assert.True(cache.TryGet("d", out _));
    }

    [Fact]
    public void TryGet_Should_PromoteAccessedEntryAboveLru()
    {
        var cache = new LruCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        cache.Put("b", 2);
        cache.Put("c", 3);
        // Access "a" to promote it — "b" becomes LRU.
        cache.TryGet("a", out _);

        cache.Put("d", 4); // Should evict "b" (now LRU).

        Assert.False(cache.TryGet("b", out _));
        Assert.True(cache.TryGet("a", out _));
        Assert.True(cache.TryGet("c", out _));
        Assert.True(cache.TryGet("d", out _));
    }

    [Fact]
    public void Put_Update_Should_PromoteUpdatedEntry()
    {
        var cache = new LruCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        cache.Put("b", 2);
        cache.Put("c", 3);
        // Re-put "a" to promote it — "b" becomes LRU.
        cache.Put("a", 10);

        cache.Put("d", 4); // Should evict "b".

        Assert.False(cache.TryGet("b", out _));
        Assert.True(cache.TryGet("a", out int val));
        Assert.Equal(10, val);
    }

    // -------------------------------------------------------------------------
    // Count
    // -------------------------------------------------------------------------

    [Fact]
    public void Count_Should_NeverExceedCapacity()
    {
        var cache = new LruCache<int, int>(capacity: 2);
        for (int i = 0; i < 10; i++)
            cache.Put(i, i);
        Assert.Equal(2, cache.Count);
    }
}
