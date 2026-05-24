namespace Katas.Tests.Disposables;

using Katas.Disposables;

public sealed class CompositeDisposableTests
{
    // A simple disposable that records how many times it was disposed.
    private sealed class TrackingDisposable : IDisposable
    {
        public int DisposeCount { get; private set; }
        public void Dispose() => DisposeCount++;
    }

    [Fact]
    public void Dispose_Should_DisposeAllChildren()
    {
        CompositeDisposable composite = new();
        TrackingDisposable a = new();
        TrackingDisposable b = new();
        composite.Add(a);
        composite.Add(b);

        composite.Dispose();

        Assert.Equal(1, a.DisposeCount);
        Assert.Equal(1, b.DisposeCount);
    }

    [Fact]
    public void Dispose_Should_DisposeChildrenInReverseOrder()
    {
        CompositeDisposable composite = new();
        var order = new List<int>();

        composite.Add(new ActionDisposable(() => order.Add(1)));
        composite.Add(new ActionDisposable(() => order.Add(2)));
        composite.Add(new ActionDisposable(() => order.Add(3)));

        composite.Dispose();

        Assert.Equal(new[] { 3, 2, 1 }, order);
    }

    [Fact]
    public void Dispose_Should_BeIdempotent_WhenCalledMultipleTimes()
    {
        CompositeDisposable composite = new();
        TrackingDisposable child = new();
        composite.Add(child);

        composite.Dispose();
        composite.Dispose(); // second dispose must not throw or re-dispose children

        Assert.Equal(1, child.DisposeCount);
    }

    [Fact]
    public void Add_Should_ThrowObjectDisposedException_AfterCompositeIsDisposed()
    {
        CompositeDisposable composite = new();
        composite.Dispose();

        Assert.Throws<ObjectDisposedException>(() => composite.Add(new TrackingDisposable()));
    }

    [Fact]
    public void UsingBlock_Should_DisposeAllChildren()
    {
        TrackingDisposable child = new();

        using (CompositeDisposable composite = new())
        {
            composite.Add(child);
        }

        Assert.Equal(1, child.DisposeCount);
    }

    // Helper: runs an action on Dispose.
    private sealed class ActionDisposable : IDisposable
    {
        private readonly Action _action;
        public ActionDisposable(Action action) => _action = action;
        public void Dispose() => _action();
    }
}
