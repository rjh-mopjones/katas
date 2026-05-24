namespace Katas.Tests.Channels;

using Katas.Channels;

public sealed class BoundedQueueTests
{
    // -------------------------------------------------------------------------
    // Basic round-trip
    // -------------------------------------------------------------------------

    [Fact]
    public async Task WriteAsync_And_ReadAllAsync_Should_ProduceEveryItemExactlyOnce()
    {
        var queue = new BoundedQueue<int>(10);

        // Write 5 items then complete.
        for (int i = 0; i < 5; i++)
            await queue.WriteAsync(i);
        queue.Complete();

        var results = new List<int>();
        await foreach (int item in queue.ReadAllAsync())
            results.Add(item);

        Assert.Equal(new[] { 0, 1, 2, 3, 4 }, results);
    }

    [Fact]
    public async Task ReadAllAsync_Should_TerminateAfterComplete_WhenBufferDrained()
    {
        var queue = new BoundedQueue<string>(5);
        await queue.WriteAsync("hello");
        await queue.WriteAsync("world");
        queue.Complete();

        var items = new List<string>();
        await foreach (string s in queue.ReadAllAsync())
            items.Add(s);

        Assert.Equal(2, items.Count);
    }

    // -------------------------------------------------------------------------
    // Backpressure
    // -------------------------------------------------------------------------

    [Fact]
    public async Task WriteAsync_Should_NotComplete_UntilReaderDrains_WhenCapacityIsOne()
    {
        var queue = new BoundedQueue<int>(1);

        // Fill the single slot.
        await queue.WriteAsync(1);

        // A second write should not complete until the reader drains the first item.
        ValueTask secondWrite = queue.WriteAsync(2);
        Assert.False(secondWrite.IsCompleted, "Second WriteAsync must be pending when channel is full.");

        // Drain one item to free a slot.
        var readTask = queue.ReadAllAsync().GetAsyncEnumerator();
        await readTask.MoveNextAsync(); // reads item 1

        // Now the second write should be able to complete.
        await secondWrite;

        await readTask.DisposeAsync();
    }

    [Fact]
    public async Task Pipeline_Should_SumAllItems_WithBackpressure()
    {
        // 3 producers, 10 items each (0..9), capacity 2 → sum = 3 * 45 = 135
        long sum = await Pipeline.SumProducedAsync(producerCount: 3, itemsPerProducer: 10, capacity: 2);
        Assert.Equal(135L, sum);
    }

    // -------------------------------------------------------------------------
    // Complete
    // -------------------------------------------------------------------------

    [Fact]
    public async Task ReadAllAsync_Should_ReturnEmpty_WhenCompleteCalledImmediately()
    {
        var queue = new BoundedQueue<int>(4);
        queue.Complete();

        var items = new List<int>();
        await foreach (int item in queue.ReadAllAsync())
            items.Add(item);

        Assert.Empty(items);
    }
}
