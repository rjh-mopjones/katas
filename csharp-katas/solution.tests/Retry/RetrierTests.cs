namespace Katas.Tests.Retry;

using Katas.Retry;
using Microsoft.Extensions.Time.Testing;

public sealed class RetrierTests
{
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /// <summary>
    /// Returns an action that fails synchronously the first <paramref name="failTimes"/> calls,
    /// then succeeds returning <paramref name="successValue"/>.
    /// </summary>
    /// <remarks>
    /// The action returns an already-completed Task so that the Retrier's <c>ExecuteAsync</c>
    /// loop advances synchronously from the exception catch to the <c>Task.Delay</c> call,
    /// registering the timer on the <see cref="FakeTimeProvider"/> before <c>Advance</c> is
    /// called. If the action used <c>async/await</c> internally the continuation would be posted
    /// to the thread pool, creating a race between timer registration and <c>Advance</c>.
    /// </remarks>
    private static Func<CancellationToken, Task<int>> SyncFailThenSucceed(
        int failTimes, int successValue = 42)
    {
        int calls = 0;
        return ct =>
        {
            calls++;
            if (calls <= failTimes)
                return Task.FromException<int>(new InvalidOperationException($"Simulated failure #{calls}"));
            return Task.FromResult(successValue);
        };
    }

    private static RetryPolicy MakePolicy(int maxAttempts, bool jitter = false) =>
        new(maxAttempts,
            BaseDelay: TimeSpan.FromSeconds(1),
            MaxDelay: TimeSpan.FromSeconds(10),
            Multiplier: 2.0,
            UseJitter: jitter);

    /// <summary>
    /// Deterministically pumps <paramref name="fake"/> forward in small steps, yielding between
    /// each, until <paramref name="task"/> completes. A single fixed <c>Advance</c> races the
    /// Retrier's thread-pool continuation: if the next <c>Task.Delay</c> timer is registered after
    /// the Advance, it is scheduled past the advanced time and the await hangs. Pumping in steps
    /// guarantees every registered timer is eventually reached. The real-time cap turns a genuine
    /// deadlock into a fast failure instead of a 45-second hang.
    /// </summary>
    private static async Task Drive(FakeTimeProvider fake, Task task)
    {
        var sw = System.Diagnostics.Stopwatch.StartNew();
        while (!task.IsCompleted && sw.Elapsed < TimeSpan.FromSeconds(5))
        {
            fake.Advance(TimeSpan.FromMilliseconds(250));
            await Task.Yield();
        }
    }

    // -------------------------------------------------------------------------
    // Success path
    // -------------------------------------------------------------------------

    [Fact]
    public async Task ExecuteAsync_Should_ReturnResult_WhenActionSucceedsOnFirstAttempt()
    {
        var retrier = new Retrier(MakePolicy(3));
        int result = await retrier.ExecuteAsync(_ => Task.FromResult(99));
        Assert.Equal(99, result);
    }

    [Fact]
    public async Task ExecuteAsync_Should_SucceedAfterNFailures_AndDriveDelaysWithFakeTime()
    {
        // Policy: maxAttempts=3, base=1 s, multiplier=2 → delays 1 s, 2 s.
        var fake = new FakeTimeProvider();
        var retrier = new Retrier(MakePolicy(3), fake, jitterSource: () => 0.0);

        // Start the operation WITHOUT awaiting.
        // Because the action is synchronous, ExecuteAsync runs up to Task.Delay(1s, fake)
        // and registers the timer before returning the incomplete Task to the test.
        Task<int> task = retrier.ExecuteAsync(SyncFailThenSucceed(failTimes: 2));

        // Deterministically pump the fake clock until the retrier completes.
        await Drive(fake, task);

        int result = await task;
        Assert.Equal(42, result);
    }

    // -------------------------------------------------------------------------
    // Exhaustion / rethrow
    // -------------------------------------------------------------------------

    [Fact]
    public async Task ExecuteAsync_Should_RethrowLastException_AfterExhaustingAttempts()
    {
        var fake = new FakeTimeProvider();
        var retrier = new Retrier(MakePolicy(3), fake);

        int calls = 0;
        Task<int> task = retrier.ExecuteAsync(ct =>
        {
            calls++;
            return Task.FromException<int>(new NotSupportedException($"Always fails #{calls}"));
        });

        await Drive(fake, task);

        NotSupportedException ex = await Assert.ThrowsAsync<NotSupportedException>(() => task);
        Assert.Equal(3, calls);
        Assert.Contains("#3", ex.Message);
    }

    [Fact]
    public async Task ExecuteAsync_Should_AttemptExactlyMaxAttemptsTimes()
    {
        var fake = new FakeTimeProvider();
        var policy = MakePolicy(5);
        var retrier = new Retrier(policy, fake);

        int calls = 0;
        Task<int> task = retrier.ExecuteAsync(ct =>
        {
            calls++;
            return Task.FromException<int>(new Exception("Always fail"));
        });

        await Drive(fake, task);

        await Assert.ThrowsAsync<Exception>(() => task);
        Assert.Equal(5, calls);
    }

    // -------------------------------------------------------------------------
    // Jitter bounds
    // -------------------------------------------------------------------------

    [Fact]
    public async Task ExecuteAsync_Should_ComputeJitteredDelay_WithinBounds()
    {
        // Fixed jitter fraction: 0.75 → delay before attempt 2 = 1 s * 0.75 = 750 ms.
        const double jitterFraction = 0.75;
        var fake = new FakeTimeProvider();
        var policy = new RetryPolicy(2, TimeSpan.FromSeconds(1), TimeSpan.FromSeconds(10), 1.0, UseJitter: true);
        var retrier = new Retrier(policy, fake, jitterSource: () => jitterFraction);

        int calls = 0;
        Task<int> task = retrier.ExecuteAsync(ct =>
        {
            calls++;
            if (calls < 2) return Task.FromException<int>(new Exception("fail once"));
            return Task.FromResult(7);
        });

        await Drive(fake, task);

        int result = await task;
        Assert.Equal(7, result);
    }

    // -------------------------------------------------------------------------
    // Cancellation
    // -------------------------------------------------------------------------

    [Fact]
    public async Task ExecuteAsync_Should_NotRetry_WhenCancelled()
    {
        using var cts = new CancellationTokenSource();
        cts.Cancel();

        var retrier = new Retrier(MakePolicy(5));
        int calls = 0;

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await retrier.ExecuteAsync(ct =>
            {
                calls++;
                ct.ThrowIfCancellationRequested();
                return Task.FromResult(0);
            }, cts.Token);
        });

        // Cancellation should propagate without retrying.
        Assert.Equal(1, calls);
    }
}
