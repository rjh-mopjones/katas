namespace Katas.Tests.LockFreeStack;

using Katas.LockFreeStack;

public sealed class LockFreeStackTests
{
    // -------------------------------------------------------------------------
    // Single-threaded LIFO contract
    // -------------------------------------------------------------------------

    [Fact]
    public void IsEmpty_Should_BeTrueOnNewStack()
    {
        var stack = new LockFreeStack<int>();
        Assert.True(stack.IsEmpty);
    }

    [Fact]
    public void TryPop_Should_ReturnFalseOnEmptyStack()
    {
        var stack = new LockFreeStack<int>();
        bool popped = stack.TryPop(out int item);
        Assert.False(popped);
        Assert.Equal(default, item);
    }

    [Fact]
    public void Push_TryPop_Should_FollowLifoOrder()
    {
        var stack = new LockFreeStack<int>();
        stack.Push(1);
        stack.Push(2);
        stack.Push(3);

        stack.TryPop(out int a);
        stack.TryPop(out int b);
        stack.TryPop(out int c);

        Assert.Equal(3, a);
        Assert.Equal(2, b);
        Assert.Equal(1, c);
        Assert.True(stack.IsEmpty);
    }

    [Fact]
    public void IsEmpty_Should_BeFalseAfterPushAndTrueAfterPop()
    {
        var stack = new LockFreeStack<string>();
        stack.Push("hello");
        Assert.False(stack.IsEmpty);

        stack.TryPop(out _);
        Assert.True(stack.IsEmpty);
    }

    // -------------------------------------------------------------------------
    // Concurrency stress — multiset conservation
    // -------------------------------------------------------------------------

    [Fact]
    public void ConcurrentPushAndPop_Should_ConserveAllElements()
    {
        const int pushers = 8;
        const int itemsPerPusher = 5_000;
        var stack = new LockFreeStack<int>();

        // Phase 1: all pushers push their items concurrently.
        Parallel.For(0, pushers, thread =>
        {
            for (int i = 0; i < itemsPerPusher; i++)
                stack.Push(thread * itemsPerPusher + i);
        });

        // Phase 2: collect all items (may be popped by multiple threads simultaneously).
        var popped = new ConcurrentBag<int>();
        Parallel.For(0, pushers, _ =>
        {
            while (stack.TryPop(out int item))
                popped.Add(item);
        });

        // All items pushed must have been popped — no losses and no duplicates.
        int totalPushed = pushers * itemsPerPusher;
        Assert.Equal(totalPushed, popped.Count);

        var expected = Enumerable.Range(0, totalPushed).ToHashSet();
        var actual = popped.ToHashSet();
        Assert.Equal(expected, actual);
    }
}
