using Katas.Scheduler;
using Microsoft.Extensions.Time.Testing;

namespace Katas.Tests.Scheduler;

public sealed class DelaySchedulerTests
{
    // ── Callback fires after delay, not before ─────────────────────────────

    [Fact]
    public void Schedule_Should_NotFireBeforeDelayElapses()
    {
        var fake = new FakeTimeProvider();
        using var scheduler = new DelayScheduler(fake);

        bool fired = false;
        scheduler.Schedule(() => fired = true, TimeSpan.FromSeconds(5));

        fake.Advance(TimeSpan.FromSeconds(4)); // just under
        Assert.False(fired);
    }

    [Fact]
    public void Schedule_Should_FireAfterDelayElapses()
    {
        var fake = new FakeTimeProvider();
        using var scheduler = new DelayScheduler(fake);

        bool fired = false;
        scheduler.Schedule(() => fired = true, TimeSpan.FromSeconds(5));

        fake.Advance(TimeSpan.FromSeconds(5));
        Assert.True(fired);
    }

    // ── Callbacks fire in due-time order even when scheduled out of order ──

    [Fact]
    public void Schedule_Should_FireCallbacksInDueTimeOrder()
    {
        var fake = new FakeTimeProvider();
        using var scheduler = new DelayScheduler(fake);

        var order = new List<string>();

        // Schedule in reverse order.
        scheduler.Schedule(() => order.Add("C"), TimeSpan.FromSeconds(3));
        scheduler.Schedule(() => order.Add("A"), TimeSpan.FromSeconds(1));
        scheduler.Schedule(() => order.Add("B"), TimeSpan.FromSeconds(2));

        fake.Advance(TimeSpan.FromSeconds(3));

        Assert.Equal(new[] { "A", "B", "C" }, order);
    }

    [Fact]
    public void Schedule_Should_FireCallbacksStepByStep()
    {
        var fake = new FakeTimeProvider();
        using var scheduler = new DelayScheduler(fake);

        var fired = new List<int>();
        scheduler.Schedule(() => fired.Add(1), TimeSpan.FromSeconds(1));
        scheduler.Schedule(() => fired.Add(2), TimeSpan.FromSeconds(2));

        fake.Advance(TimeSpan.FromSeconds(1));
        Assert.Equal(new[] { 1 }, fired);

        fake.Advance(TimeSpan.FromSeconds(1));
        Assert.Equal(new[] { 1, 2 }, fired);
    }

    // ── Cancelling a handle before due time prevents callback ─────────────

    [Fact]
    public void Schedule_Should_NotFireAfterHandleIsDisposed()
    {
        var fake = new FakeTimeProvider();
        using var scheduler = new DelayScheduler(fake);

        bool fired = false;
        IDisposable handle = scheduler.Schedule(() => fired = true, TimeSpan.FromSeconds(5));

        handle.Dispose();
        fake.Advance(TimeSpan.FromSeconds(5));

        Assert.False(fired);
    }

    [Fact]
    public void Schedule_Should_FireOtherCallbacksWhenOneIsCancelled()
    {
        var fake = new FakeTimeProvider();
        using var scheduler = new DelayScheduler(fake);

        bool a = false, b = false;
        IDisposable cancelMe = scheduler.Schedule(() => a = true, TimeSpan.FromSeconds(1));
        scheduler.Schedule(() => b = true, TimeSpan.FromSeconds(1));

        cancelMe.Dispose();
        fake.Advance(TimeSpan.FromSeconds(1));

        Assert.False(a);
        Assert.True(b);
    }

    // ── Dispose stops the scheduler ────────────────────────────────────────

    [Fact]
    public void Dispose_Should_PreventPendingCallbacksFromFiring()
    {
        var fake = new FakeTimeProvider();
        var scheduler = new DelayScheduler(fake);

        bool fired = false;
        scheduler.Schedule(() => fired = true, TimeSpan.FromSeconds(1));

        scheduler.Dispose();

        // After dispose, advancing the fake clock should not fire the callback.
        fake.Advance(TimeSpan.FromSeconds(1));
        Assert.False(fired);
    }

    [Fact]
    public void Dispose_Should_BeIdempotent()
    {
        var scheduler = new DelayScheduler();
        scheduler.Dispose();

        // Second dispose should not throw.
        var ex = Record.Exception(() => scheduler.Dispose());
        Assert.Null(ex);
    }
}
