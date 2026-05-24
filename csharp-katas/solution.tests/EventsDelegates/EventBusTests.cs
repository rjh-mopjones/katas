namespace Katas.Tests.EventsDelegates;

using Katas.EventsDelegates;

public sealed class EventBusTests
{
    // -------------------------------------------------------------------------
    // Basic delivery
    // -------------------------------------------------------------------------

    [Fact]
    public void Publish_Should_DeliverEvent_ToSingleSubscriber()
    {
        EventBus bus = new();
        int received = 0;
        using IDisposable _ = bus.Subscribe<int>(n => received = n);

        bus.Publish(42);

        Assert.Equal(42, received);
    }

    [Fact]
    public void Publish_Should_DeliverEvent_ToMultipleSubscribers()
    {
        EventBus bus = new();
        var received = new List<string>();

        using IDisposable sub1 = bus.Subscribe<string>(s => received.Add("A:" + s));
        using IDisposable sub2 = bus.Subscribe<string>(s => received.Add("B:" + s));

        bus.Publish("hello");

        Assert.Contains("A:hello", received);
        Assert.Contains("B:hello", received);
        Assert.Equal(2, received.Count);
    }

    // -------------------------------------------------------------------------
    // Unsubscribe via Dispose
    // -------------------------------------------------------------------------

    [Fact]
    public void Dispose_Should_UnsubscribeHandler()
    {
        EventBus bus = new();
        int callCount = 0;

        IDisposable sub = bus.Subscribe<int>(_ => callCount++);
        bus.Publish(1);
        sub.Dispose();
        bus.Publish(2);  // should not reach handler

        Assert.Equal(1, callCount);
    }

    [Fact]
    public void Dispose_Should_BeIdempotent_WhenCalledMultipleTimes()
    {
        EventBus bus = new();
        int callCount = 0;
        IDisposable sub = bus.Subscribe<int>(_ => callCount++);

        sub.Dispose();
        sub.Dispose(); // second dispose must not throw

        bus.Publish(1);
        Assert.Equal(0, callCount);
    }

    // -------------------------------------------------------------------------
    // No-op publish
    // -------------------------------------------------------------------------

    [Fact]
    public void Publish_Should_BeNoOp_WhenNoSubscribersForType()
    {
        EventBus bus = new();
        // Subscribe to a different type to ensure the bus isn't empty
        using IDisposable _ = bus.Subscribe<string>(_ => { });

        // Publishing int with no int subscriber must not throw
        bus.Publish(99);
    }

    // -------------------------------------------------------------------------
    // Fault isolation
    // -------------------------------------------------------------------------

    [Fact]
    public void Publish_Should_DeliverToRemainingHandlers_WhenOneHandlerThrows()
    {
        EventBus bus = new();
        bool secondHandlerCalled = false;

        using IDisposable sub1 = bus.Subscribe<int>(_ => throw new InvalidOperationException("boom"));
        using IDisposable sub2 = bus.Subscribe<int>(_ => secondHandlerCalled = true);

        bus.Publish(1); // first handler throws, second should still fire

        Assert.True(secondHandlerCalled);
    }

    // -------------------------------------------------------------------------
    // Type isolation
    // -------------------------------------------------------------------------

    [Fact]
    public void Publish_Should_OnlyDeliverToHandlersMatchingExactType()
    {
        EventBus bus = new();
        bool intHandlerCalled = false;
        bool stringHandlerCalled = false;

        using IDisposable sub1 = bus.Subscribe<int>(_ => intHandlerCalled = true);
        using IDisposable sub2 = bus.Subscribe<string>(_ => stringHandlerCalled = true);

        bus.Publish("event");

        Assert.False(intHandlerCalled);
        Assert.True(stringHandlerCalled);
    }
}
