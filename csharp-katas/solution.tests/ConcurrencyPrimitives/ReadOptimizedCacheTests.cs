namespace Katas.Tests.ConcurrencyPrimitives;

using Katas.ConcurrencyPrimitives;

public sealed class ReadOptimizedCacheTests
{
    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    [Fact]
    public void TryGet_Should_ReturnFalseForMissingKey()
    {
        var cache = new ReadOptimizedCache<string, int>();
        Assert.False(cache.TryGet("missing", out _));
    }

    [Fact]
    public void GetOrAdd_Should_StoreAndReturnFactoryResult()
    {
        var cache = new ReadOptimizedCache<string, int>();
        int value = cache.GetOrAdd("key", k => 42);
        Assert.Equal(42, value);
    }

    [Fact]
    public void TryGet_Should_ReturnTrueAndValueAfterGetOrAdd()
    {
        var cache = new ReadOptimizedCache<string, int>();
        cache.GetOrAdd("key", _ => 99);

        bool found = cache.TryGet("key", out int value);

        Assert.True(found);
        Assert.Equal(99, value);
    }

    [Fact]
    public void Count_Should_ReflectNumberOfStoredEntries()
    {
        var cache = new ReadOptimizedCache<string, int>();
        Assert.Equal(0, cache.Count);
        cache.GetOrAdd("a", _ => 1);
        Assert.Equal(1, cache.Count);
        cache.GetOrAdd("b", _ => 2);
        Assert.Equal(2, cache.Count);
    }

    // -------------------------------------------------------------------------
    // Factory-once guarantee
    // -------------------------------------------------------------------------

    [Fact]
    public void GetOrAdd_Should_InvokeFactoryExactlyOncePerKey()
    {
        var cache = new ReadOptimizedCache<string, int>();
        int callCount = 0;

        for (int i = 0; i < 10; i++)
            cache.GetOrAdd("key", _ => { callCount++; return 1; });

        Assert.Equal(1, callCount);
    }

    // -------------------------------------------------------------------------
    // Concurrency — factory called once even under parallel GetOrAdd
    // -------------------------------------------------------------------------

    [Fact]
    public void GetOrAdd_Should_ComputeOncePerKeyUnderParallelContention()
    {
        var cache = new ReadOptimizedCache<string, int>();
        int callCount = 0;

        Parallel.For(0, 100, _ =>
        {
            cache.GetOrAdd("shared-key", _ =>
            {
                Interlocked.Increment(ref callCount);
                return 7;
            });
        });

        // The upgradeable-read lock guarantees exactly one factory invocation.
        Assert.Equal(1, callCount);

        bool found = cache.TryGet("shared-key", out int value);
        Assert.True(found);
        Assert.Equal(7, value);
    }
}
