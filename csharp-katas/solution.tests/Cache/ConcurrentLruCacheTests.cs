namespace Katas.Tests.Cache;

using Katas.Cache;

public sealed class ConcurrentLruCacheTests
{
    // -------------------------------------------------------------------------
    // Basic behaviour (mirroring LruCache to confirm wrapper correctness)
    // -------------------------------------------------------------------------

    [Fact]
    public void TryGet_Should_ReturnFalseForMissingKey()
    {
        var cache = new ConcurrentLruCache<string, int>(capacity: 3);
        Assert.False(cache.TryGet("x", out _));
    }

    [Fact]
    public void Put_TryGet_Should_StoreAndReturnValue()
    {
        var cache = new ConcurrentLruCache<string, int>(capacity: 3);
        cache.Put("key", 42);
        bool found = cache.TryGet("key", out int value);
        Assert.True(found);
        Assert.Equal(42, value);
    }

    [Fact]
    public void Put_Should_EvictLruOnOverflow()
    {
        var cache = new ConcurrentLruCache<string, int>(capacity: 2);
        cache.Put("a", 1);
        cache.Put("b", 2);
        cache.Put("c", 3); // Evicts "a".

        Assert.False(cache.TryGet("a", out _));
        Assert.True(cache.TryGet("b", out _));
        Assert.True(cache.TryGet("c", out _));
    }

    // -------------------------------------------------------------------------
    // Parallel stress test
    // -------------------------------------------------------------------------

    [Fact]
    public void ConcurrentPutAndGet_Should_NeverExceedCapacityAndThrowNothing()
    {
        const int capacity = 10;
        const int workers = 20;
        const int opsPerWorker = 2_000;
        var cache = new ConcurrentLruCache<int, int>(capacity);
        var rng = new ThreadLocal<Random>(() => new Random(Environment.TickCount));

        var exceptions = new ConcurrentBag<Exception>();

        Parallel.For(0, workers, workerId =>
        {
            try
            {
                for (int i = 0; i < opsPerWorker; i++)
                {
                    int key = rng.Value!.Next(0, 20); // Deliberately overlap keys to stress eviction.
                    if (i % 2 == 0)
                        cache.Put(key, workerId * opsPerWorker + i);
                    else
                        cache.TryGet(key, out _);

                    int count = cache.Count;
                    if (count > capacity)
                        throw new InvalidOperationException($"Count {count} exceeded capacity {capacity}.");
                }
            }
            catch (Exception ex)
            {
                exceptions.Add(ex);
            }
        });

        Assert.Empty(exceptions);
        Assert.True(cache.Count <= capacity,
            $"Final count {cache.Count} exceeded capacity {capacity}.");
    }
}
