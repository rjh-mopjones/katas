namespace Katas.Tests.Cache;

using Katas.Cache;

public sealed class LfuCacheTests
{
    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    [Fact]
    public void TryGet_Should_ReturnFalseForMissingKey()
    {
        var cache = new LfuCache<string, int>(capacity: 3);
        Assert.False(cache.TryGet("missing", out _));
    }

    [Fact]
    public void Put_TryGet_Should_StoreAndReturnValue()
    {
        var cache = new LfuCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        bool found = cache.TryGet("a", out int value);
        Assert.True(found);
        Assert.Equal(1, value);
    }

    [Fact]
    public void Put_Should_UpdateExistingKey()
    {
        var cache = new LfuCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        cache.Put("a", 99);
        cache.TryGet("a", out int value);
        Assert.Equal(99, value);
        Assert.Equal(1, cache.Count);
    }

    // -------------------------------------------------------------------------
    // Eviction — least-frequently-used
    // -------------------------------------------------------------------------

    [Fact]
    public void Put_Should_EvictLeastFrequentEntryOnOverflow()
    {
        var cache = new LfuCache<string, int>(capacity: 3);
        cache.Put("a", 1);
        cache.Put("b", 2);
        cache.Put("c", 3);

        // Access "a" and "b" to raise their frequencies.
        cache.TryGet("a", out _); // freq(a) = 2
        cache.TryGet("b", out _); // freq(b) = 2
        // freq(c) = 1 — least frequent

        cache.Put("d", 4); // Should evict "c".

        Assert.False(cache.TryGet("c", out _));
        Assert.True(cache.TryGet("a", out _));
        Assert.True(cache.TryGet("b", out _));
        Assert.True(cache.TryGet("d", out _));
    }

    // -------------------------------------------------------------------------
    // Tie-breaking — LRU within same frequency
    // -------------------------------------------------------------------------

    [Fact]
    public void Put_Should_EvictLruEntryAmongTiedFrequencies()
    {
        var cache = new LfuCache<string, int>(capacity: 2);
        // Insert both at frequency 1.
        cache.Put("a", 1); // inserted first → LRU within freq=1
        cache.Put("b", 2);

        // Both have freq=1; "a" is the LRU (inserted earlier).
        cache.Put("c", 3); // Should evict "a".

        Assert.False(cache.TryGet("a", out _));
        Assert.True(cache.TryGet("b", out _));
        Assert.True(cache.TryGet("c", out _));
    }

    // -------------------------------------------------------------------------
    // Frequency increments
    // -------------------------------------------------------------------------

    [Fact]
    public void TryGet_Should_IncrementFrequencyAndPreserveEntry()
    {
        var cache = new LfuCache<string, int>(capacity: 2);
        cache.Put("a", 10);

        // Access "a" many times to raise its frequency above any eviction candidate.
        for (int i = 0; i < 5; i++)
            cache.TryGet("a", out _);

        cache.Put("b", 20);
        cache.Put("c", 30); // "b" should be evicted (freq=1), not "a" (freq=6).

        Assert.True(cache.TryGet("a", out int val));
        Assert.Equal(10, val);
        Assert.False(cache.TryGet("b", out _));
    }

    // -------------------------------------------------------------------------
    // Count
    // -------------------------------------------------------------------------

    [Fact]
    public void Count_Should_NeverExceedCapacity()
    {
        var cache = new LfuCache<int, int>(capacity: 3);
        for (int i = 0; i < 10; i++)
            cache.Put(i, i);
        Assert.Equal(3, cache.Count);
    }
}
