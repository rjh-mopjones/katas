namespace Katas.Tests.AsyncStreams;

using Katas.AsyncStreams;

public sealed class AsyncSequenceOperatorsTests
{
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static async IAsyncEnumerable<int> RangeAsync(int start, int count)
    {
        for (int i = start; i < start + count; i++)
        {
            await Task.Yield();
            yield return i;
        }
    }

    private static async Task<List<T>> ToListAsync<T>(IAsyncEnumerable<T> source)
    {
        var list = new List<T>();
        await foreach (T item in source)
            list.Add(item);
        return list;
    }

    // -------------------------------------------------------------------------
    // SelectAsync
    // -------------------------------------------------------------------------

    [Fact]
    public async Task SelectAsync_Should_ProjectEachElement()
    {
        IAsyncEnumerable<int> source = RangeAsync(0, 5);
        IAsyncEnumerable<string> result = source.SelectAsync(n => n.ToString());

        List<string> list = await ToListAsync(result);

        Assert.Equal(new[] { "0", "1", "2", "3", "4" }, list);
    }

    [Fact]
    public async Task SelectAsync_Should_ReturnEmptySequence_WhenSourceIsEmpty()
    {
        IAsyncEnumerable<int> source = RangeAsync(0, 0);
        List<int> result = await ToListAsync(source.SelectAsync(n => n * 2));
        Assert.Empty(result);
    }

    // -------------------------------------------------------------------------
    // WhereAsync
    // -------------------------------------------------------------------------

    [Fact]
    public async Task WhereAsync_Should_ReturnOnlyMatchingElements()
    {
        IAsyncEnumerable<int> source = RangeAsync(0, 10);
        List<int> evens = await ToListAsync(source.WhereAsync(n => n % 2 == 0));

        Assert.Equal(new[] { 0, 2, 4, 6, 8 }, evens);
    }

    [Fact]
    public async Task WhereAsync_Should_ReturnEmpty_WhenNoElementsMatch()
    {
        IAsyncEnumerable<int> source = RangeAsync(0, 5);
        List<int> result = await ToListAsync(source.WhereAsync(n => n > 100));
        Assert.Empty(result);
    }

    // -------------------------------------------------------------------------
    // TakeAsync
    // -------------------------------------------------------------------------

    [Fact]
    public async Task TakeAsync_Should_ReturnAtMostCountElements()
    {
        IAsyncEnumerable<int> source = RangeAsync(0, 100);
        List<int> result = await ToListAsync(source.TakeAsync(3));

        Assert.Equal(new[] { 0, 1, 2 }, result);
    }

    [Fact]
    public async Task TakeAsync_Should_ReturnAllElements_WhenSourceShorterThanCount()
    {
        IAsyncEnumerable<int> source = RangeAsync(0, 2);
        List<int> result = await ToListAsync(source.TakeAsync(10));

        Assert.Equal(new[] { 0, 1 }, result);
    }

    [Fact]
    public async Task TakeAsync_Should_ReturnEmpty_WhenCountIsZero()
    {
        IAsyncEnumerable<int> source = RangeAsync(0, 10);
        List<int> result = await ToListAsync(source.TakeAsync(0));
        Assert.Empty(result);
    }

    // -------------------------------------------------------------------------
    // Composition
    // -------------------------------------------------------------------------

    [Fact]
    public async Task Select_Where_Take_Should_ComposeCorrectly()
    {
        // 0..19 -> double -> keep even-indexed doubles -> take first 3
        // doubled: 0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,32,34,36,38
        // where divisible by 4: 0,4,8,12,16,20,24,28,32,36
        // take 3: 0,4,8
        IAsyncEnumerable<int> source = RangeAsync(0, 20);
        IAsyncEnumerable<int> pipeline = source
            .SelectAsync(n => n * 2)
            .WhereAsync(n => n % 4 == 0)
            .TakeAsync(3);

        List<int> result = await ToListAsync(pipeline);

        Assert.Equal(new[] { 0, 4, 8 }, result);
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    [Fact]
    public async Task SelectAsync_Should_ThrowOperationCanceledException_WhenTokenAlreadyCancelled()
    {
        using var cts = new CancellationTokenSource();
        cts.Cancel();

        IAsyncEnumerable<int> source = RangeAsync(0, 10);

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await foreach (int n in source.SelectAsync(x => x, cts.Token)) { }
        });
    }

    [Fact]
    public async Task WhereAsync_Should_ThrowOperationCanceledException_WhenTokenAlreadyCancelled()
    {
        using var cts = new CancellationTokenSource();
        cts.Cancel();

        IAsyncEnumerable<int> source = RangeAsync(0, 10);

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await foreach (int n in source.WhereAsync(x => true, cts.Token)) { }
        });
    }

    [Fact]
    public async Task TakeAsync_Should_ThrowOperationCanceledException_WhenTokenAlreadyCancelled()
    {
        using var cts = new CancellationTokenSource();
        cts.Cancel();

        // Use NumberSource so we get a real ct-aware producer
        var ns = new NumberSource();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await foreach (int n in ns.ProduceAsync(10).TakeAsync(5, cts.Token)) { }
        });
    }

    [Fact]
    public async Task SelectAsync_Should_ThrowOperationCanceledException_WhenCancelledMidStream()
    {
        using var cts = new CancellationTokenSource();
        var source = new NumberSource();
        var results = new List<int>();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await foreach (int n in source.ProduceAsync(100).SelectAsync(x => x, cts.Token))
            {
                results.Add(n);
                if (n == 3) cts.Cancel();
            }
        });

        Assert.NotEmpty(results);
    }
}
