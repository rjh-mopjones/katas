namespace Katas.Tests.ScatterGather;

using Katas.ScatterGather;
using Microsoft.Extensions.Time.Testing;

public sealed class ScatterTests
{
    // -------------------------------------------------------------------------
    // GatherAllAsync
    // -------------------------------------------------------------------------

    [Fact]
    public async Task GatherAllAsync_Should_ReturnAllResultsInInputOrder()
    {
        // Three tasks completing in reversed order; results must still be in input order.
        var tcs0 = new TaskCompletionSource<int>();
        var tcs1 = new TaskCompletionSource<int>();
        var tcs2 = new TaskCompletionSource<int>();

        var factories = new Func<CancellationToken, Task<int>>[]
        {
            _ => tcs0.Task,
            _ => tcs1.Task,
            _ => tcs2.Task,
        };

        Task<IReadOnlyList<int>> gather = Scatter.GatherAllAsync(factories);

        // Complete in reverse order.
        tcs2.SetResult(200);
        tcs1.SetResult(100);
        tcs0.SetResult(0);

        IReadOnlyList<int> results = await gather;

        Assert.Equal(new[] { 0, 100, 200 }, results);
    }

    [Fact]
    public async Task GatherAllAsync_Should_ReturnEmptyList_WhenNoTasks()
    {
        IReadOnlyList<int> results = await Scatter.GatherAllAsync(
            Array.Empty<Func<CancellationToken, Task<int>>>());
        Assert.Empty(results);
    }

    [Fact]
    public async Task GatherAllAsync_Should_PropagateException_WhenAnyTaskFaults()
    {
        var factories = new Func<CancellationToken, Task<int>>[]
        {
            async ct => { await Task.Yield(); return 1; },
            async ct => { await Task.Yield(); throw new InvalidOperationException("boom"); },
            async ct => { await Task.Yield(); return 3; },
        };

        // WhenAll re-throws the first faulted task's exception on await.
        await Assert.ThrowsAsync<InvalidOperationException>(
            () => Scatter.GatherAllAsync(factories));
    }

    // -------------------------------------------------------------------------
    // GatherBeforeTimeoutAsync
    // -------------------------------------------------------------------------

    [Fact]
    public async Task GatherBeforeTimeoutAsync_Should_ReturnOnlyFastResults_AndDropSlowOnes()
    {
        // Task 0: completes immediately (via Task.FromResult path).
        // Task 1: never completes — will be dropped on timeout.
        // Task 2: completes immediately.
        var neverTcs = new TaskCompletionSource<int>();
        var fake = new FakeTimeProvider();

        var factories = new Func<CancellationToken, Task<int>>[]
        {
            _ => Task.FromResult(10),
            _ => neverTcs.Task,
            _ => Task.FromResult(30),
        };

        Task<IReadOnlyList<int>> gather = Scatter.GatherBeforeTimeoutAsync(
            factories,
            timeout: TimeSpan.FromSeconds(1),
            timeProvider: fake);

        // Yield to let GatherBeforeTimeoutAsync register its WaitAsync timers on the
        // FakeTimeProvider before we advance the clock.
        await Task.Yield();

        // Advance fake time past the timeout to fire the WaitAsync TimeoutException for task 1.
        fake.Advance(TimeSpan.FromSeconds(2));
        await Task.Yield();

        IReadOnlyList<int> results = await gather;

        Assert.Equal(new[] { 10, 30 }, results);
    }

    [Fact]
    public async Task GatherBeforeTimeoutAsync_Should_ReturnAll_WhenAllCompleteBeforeTimeout()
    {
        var fake = new FakeTimeProvider();

        var factories = new Func<CancellationToken, Task<int>>[]
        {
            _ => Task.FromResult(1),
            _ => Task.FromResult(2),
        };

        IReadOnlyList<int> results = await Scatter.GatherBeforeTimeoutAsync(
            factories,
            timeout: TimeSpan.FromSeconds(10),
            timeProvider: fake);

        Assert.Equal(new[] { 1, 2 }, results);
    }

    [Fact]
    public async Task GatherBeforeTimeoutAsync_Should_ReturnEmpty_WhenAllTasksTimeout()
    {
        var fake = new FakeTimeProvider();
        var never1 = new TaskCompletionSource<int>();
        var never2 = new TaskCompletionSource<int>();

        var factories = new Func<CancellationToken, Task<int>>[]
        {
            _ => never1.Task,
            _ => never2.Task,
        };

        Task<IReadOnlyList<int>> gather = Scatter.GatherBeforeTimeoutAsync(
            factories,
            timeout: TimeSpan.FromSeconds(1),
            timeProvider: fake);

        await Task.Yield();
        fake.Advance(TimeSpan.FromSeconds(5));
        await Task.Yield();

        IReadOnlyList<int> results = await gather;

        Assert.Empty(results);
    }

    [Fact]
    public async Task GatherBeforeTimeoutAsync_Should_DropFaultedTasks()
    {
        var fake = new FakeTimeProvider();

        var factories = new Func<CancellationToken, Task<int>>[]
        {
            _ => Task.FromResult(1),
            _ => Task.FromException<int>(new Exception("faulted")),
        };

        IReadOnlyList<int> results = await Scatter.GatherBeforeTimeoutAsync(
            factories,
            timeout: TimeSpan.FromSeconds(5),
            timeProvider: fake);

        Assert.Equal(new[] { 1 }, results);
    }
}
