namespace Katas.Tests.AsyncStreams;

using Katas.AsyncStreams;

public sealed class NumberSourceTests
{
    [Fact]
    public async Task ProduceAsync_Should_YieldSequentialIntegers()
    {
        var source = new NumberSource();
        var results = new List<int>();

        await foreach (int n in source.ProduceAsync(5))
            results.Add(n);

        Assert.Equal(new[] { 0, 1, 2, 3, 4 }, results);
    }

    [Fact]
    public async Task ProduceAsync_Should_YieldZeroItems_WhenCountIsZero()
    {
        var source = new NumberSource();
        var results = new List<int>();

        await foreach (int n in source.ProduceAsync(0))
            results.Add(n);

        Assert.Empty(results);
    }

    [Fact]
    public async Task ProduceAsync_Should_ThrowOperationCanceledException_WhenTokenAlreadyCancelled()
    {
        var source = new NumberSource();
        using var cts = new CancellationTokenSource();
        cts.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await foreach (int n in source.ProduceAsync(10, cts.Token))
            {
                // should not reach here
            }
        });
    }

    [Fact]
    public async Task ProduceAsync_Should_ThrowOperationCanceledException_WhenCancelledMidStream()
    {
        var source = new NumberSource();
        using var cts = new CancellationTokenSource();
        var results = new List<int>();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await foreach (int n in source.ProduceAsync(100, cts.Token))
            {
                results.Add(n);
                if (n == 2) cts.Cancel();
            }
        });

        // We consumed at least one item before cancellation
        Assert.NotEmpty(results);
    }
}
