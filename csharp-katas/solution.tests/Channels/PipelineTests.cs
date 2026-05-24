namespace Katas.Tests.Channels;

using Katas.Channels;

public sealed class PipelineTests
{
    [Fact]
    public async Task SumProducedAsync_Should_SumAllItemsFromSingleProducer()
    {
        // 1 producer, 5 items (0+1+2+3+4 = 10)
        long sum = await Pipeline.SumProducedAsync(1, 5, capacity: 10);
        Assert.Equal(10L, sum);
    }

    [Fact]
    public async Task SumProducedAsync_Should_SumAllItemsFromMultipleProducers()
    {
        // 4 producers, 5 items each (0..4), sum per producer = 10, total = 40
        long sum = await Pipeline.SumProducedAsync(4, 5, capacity: 10);
        Assert.Equal(40L, sum);
    }

    [Fact]
    public async Task SumProducedAsync_Should_SumCorrectly_WithTinyCapacity()
    {
        // capacity=1 forces heavy backpressure; correctness must still hold.
        // 2 producers, 10 items each (0..9), sum each = 45, total = 90
        long sum = await Pipeline.SumProducedAsync(2, 10, capacity: 1);
        Assert.Equal(90L, sum);
    }

    [Fact]
    public async Task SumProducedAsync_Should_ReturnZero_WhenNoItemsProduced()
    {
        long sum = await Pipeline.SumProducedAsync(5, 0, capacity: 4);
        Assert.Equal(0L, sum);
    }

    [Fact]
    public async Task SumProducedAsync_Should_ReturnZero_WhenNoProducers()
    {
        long sum = await Pipeline.SumProducedAsync(0, 100, capacity: 4);
        Assert.Equal(0L, sum);
    }
}
