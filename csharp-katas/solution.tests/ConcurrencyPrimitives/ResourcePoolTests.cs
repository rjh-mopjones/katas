namespace Katas.Tests.ConcurrencyPrimitives;

using Katas.ConcurrencyPrimitives;

public sealed class ResourcePoolTests
{
    // -------------------------------------------------------------------------
    // Basic behaviour
    // -------------------------------------------------------------------------

    [Fact]
    public async Task AcquireAsync_Should_ReturnFactoryCreatedInstance()
    {
        int created = 0;
        var pool = new ResourcePool<string>(() => { created++; return $"instance-{created}"; }, maxSize: 2);

        string item = await pool.AcquireAsync();

        Assert.NotNull(item);
        Assert.Equal(1, created);
    }

    [Fact]
    public async Task Release_Should_AllowSubsequentAcquireWithoutCreatingNewInstance()
    {
        int created = 0;
        var pool = new ResourcePool<string>(() => { created++; return $"instance-{created}"; }, maxSize: 2);

        string first = await pool.AcquireAsync();
        pool.Release(first);
        string second = await pool.AcquireAsync();

        Assert.Same(first, second);
        Assert.Equal(1, created); // Factory should only have been called once.
    }

    [Fact]
    public async Task Available_Should_DecrementOnAcquireAndIncrementOnRelease()
    {
        var pool = new ResourcePool<object>(() => new object(), maxSize: 3);

        Assert.Equal(3, pool.Available);
        var a = await pool.AcquireAsync();
        Assert.Equal(2, pool.Available);
        pool.Release(a);
        Assert.Equal(3, pool.Available);
    }

    // -------------------------------------------------------------------------
    // Bounded concurrency
    // -------------------------------------------------------------------------

    [Fact]
    public async Task AcquireAsync_Should_LimitConcurrentBorrowsToMaxSize()
    {
        const int maxSize = 4;
        const int totalWorkers = 20;
        var pool = new ResourcePool<object>(() => new object(), maxSize);

        int concurrent = 0;
        int maxObservedConcurrent = 0;
        var syncRoot = new object();

        var tasks = Enumerable.Range(0, totalWorkers).Select(async _ =>
        {
            var item = await pool.AcquireAsync();
            int c = Interlocked.Increment(ref concurrent);
            lock (syncRoot)
                if (c > maxObservedConcurrent)
                    maxObservedConcurrent = c;

            await Task.Delay(20); // Hold the resource briefly.
            Interlocked.Decrement(ref concurrent);
            pool.Release(item);
        });

        await Task.WhenAll(tasks);

        Assert.True(maxObservedConcurrent <= maxSize,
            $"Observed {maxObservedConcurrent} concurrent borrows; limit was {maxSize}.");
    }

    [Fact]
    public async Task Release_Should_UnblockWaitingAcquire()
    {
        var pool = new ResourcePool<string>(() => "item", maxSize: 1);

        // Acquire the only permit.
        string held = await pool.AcquireAsync();

        // Start a waiter that will block.
        var waiterTask = pool.AcquireAsync();
        Assert.False(waiterTask.IsCompleted);

        // Release the permit — waiter should unblock.
        pool.Release(held);
        string acquired = await waiterTask.WaitAsync(TimeSpan.FromSeconds(5));
        Assert.Equal("item", acquired);
    }
}
