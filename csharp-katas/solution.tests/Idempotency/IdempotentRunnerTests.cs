using System.Collections.Concurrent;
using Katas.Idempotency;

namespace Katas.Tests.Idempotency;

public sealed class IdempotentRunnerTests
{
    // ── Action runs exactly once per key ──────────────────────────────────

    [Fact]
    public async Task RunOnceAsync_Should_RunActionOnlyOnceForSameKey()
    {
        var runner = new IdempotentRunner();
        int callCount = 0;

        await runner.RunOnceAsync("key1", async _ =>
        {
            Interlocked.Increment(ref callCount);
            await Task.Yield();
            return 1;
        });

        await runner.RunOnceAsync("key1", async _ =>
        {
            Interlocked.Increment(ref callCount);
            await Task.Yield();
            return 2;
        });

        Assert.Equal(1, callCount);
    }

    [Fact]
    public async Task RunOnceAsync_Should_ReturnSameResultForRepeatedCalls()
    {
        var runner = new IdempotentRunner();

        int first = await runner.RunOnceAsync("key", _ => Task.FromResult(42));
        int second = await runner.RunOnceAsync("key", _ => Task.FromResult(99));

        Assert.Equal(42, first);
        Assert.Equal(42, second);
    }

    // ── Different keys run independently ─────────────────────────────────

    [Fact]
    public async Task RunOnceAsync_Should_RunActionForEachDistinctKey()
    {
        var runner = new IdempotentRunner();

        int a = await runner.RunOnceAsync("a", _ => Task.FromResult(1));
        int b = await runner.RunOnceAsync("b", _ => Task.FromResult(2));

        Assert.Equal(1, a);
        Assert.Equal(2, b);
    }

    [Fact]
    public async Task RunOnceAsync_Should_RunEachKeyActionOnce()
    {
        var runner = new IdempotentRunner();
        var counts = new ConcurrentDictionary<string, int>();

        for (int i = 0; i < 5; i++)
        {
            string key = $"key-{i % 3}"; // 3 distinct keys
            await runner.RunOnceAsync(key, async _ =>
            {
                counts.AddOrUpdate(key, 1, (_, v) => v + 1);
                await Task.Yield();
                return key;
            });
        }

        // Each key's action should have run at most once.
        foreach (int count in counts.Values)
            Assert.Equal(1, count);
    }

    // ── Concurrent calls for the same key — action executes exactly once ──

    [Fact]
    public async Task RunOnceAsync_Should_ExecuteActionExactlyOnceUnderConcurrentCalls()
    {
        const int threads = 50;
        var runner = new IdempotentRunner();
        int callCount = 0;
        var barrier = new TaskCompletionSource();

        // All tasks start and race to be first for the same key.
        Task<int>[] tasks = Enumerable.Range(0, threads)
            .Select(_ => runner.RunOnceAsync("shared-key", async ct =>
            {
                await barrier.Task;
                Interlocked.Increment(ref callCount);
                return 42;
            }))
            .ToArray();

        // Release all waiting tasks simultaneously.
        barrier.SetResult();

        int[] results = await Task.WhenAll(tasks);

        Assert.Equal(1, callCount);
        Assert.All(results, r => Assert.Equal(42, r));
    }

    // ── Exception caching ─────────────────────────────────────────────────

    [Fact]
    public async Task RunOnceAsync_Should_CacheAndRethrowExceptionForSameKey()
    {
        var runner = new IdempotentRunner();
        int callCount = 0;

        Task<int> RunFailing(CancellationToken _)
        {
            Interlocked.Increment(ref callCount);
            return Task.FromException<int>(new InvalidOperationException("oops"));
        }

        await Assert.ThrowsAsync<InvalidOperationException>(() =>
            runner.RunOnceAsync("failing", RunFailing));

        // Second call returns cached faulted task — action not called again.
        await Assert.ThrowsAsync<InvalidOperationException>(() =>
            runner.RunOnceAsync("failing", RunFailing));

        Assert.Equal(1, callCount);
    }
}
