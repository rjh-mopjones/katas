namespace Katas.Tests.ConcurrencyPrimitives;

using Katas.ConcurrencyPrimitives;

public sealed class AtomicCounterTests
{
    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    [Fact]
    public void Value_Should_ReturnZeroAfterConstruction()
    {
        var counter = new AtomicCounter();
        Assert.Equal(0L, counter.Value);
    }

    [Fact]
    public void Increment_Should_ReturnNewValueAndAdvanceCounter()
    {
        var counter = new AtomicCounter();
        long result = counter.Increment();
        Assert.Equal(1L, result);
        Assert.Equal(1L, counter.Value);
    }

    [Fact]
    public void Add_Should_AddDeltaToCounter()
    {
        var counter = new AtomicCounter();
        counter.Add(10);
        Assert.Equal(10L, counter.Value);
    }

    [Fact]
    public void TryIncrementIfBelow_Should_IncrementWhenBelowMax()
    {
        var counter = new AtomicCounter();
        bool incremented = counter.TryIncrementIfBelow(5);
        Assert.True(incremented);
        Assert.Equal(1L, counter.Value);
    }

    [Fact]
    public void TryIncrementIfBelow_Should_ReturnFalseWhenAtOrAboveMax()
    {
        var counter = new AtomicCounter();
        counter.Add(5);
        bool incremented = counter.TryIncrementIfBelow(5);
        Assert.False(incremented);
        Assert.Equal(5L, counter.Value);
    }

    // -------------------------------------------------------------------------
    // Concurrency
    // -------------------------------------------------------------------------

    [Fact]
    public void Increment_Should_ReachExactTotalUnderParallelContention()
    {
        const int threads = 16;
        const int incrementsPerThread = 10_000;
        var counter = new AtomicCounter();

        Parallel.For(0, threads, _ =>
        {
            for (int i = 0; i < incrementsPerThread; i++)
                counter.Increment();
        });

        Assert.Equal((long)(threads * incrementsPerThread), counter.Value);
    }

    [Fact]
    public void TryIncrementIfBelow_Should_NeverExceedCapUnderContention()
    {
        const long cap = 100;
        const int threads = 32;
        const int attemptsPerThread = 1_000;
        var counter = new AtomicCounter();

        Parallel.For(0, threads, _ =>
        {
            for (int i = 0; i < attemptsPerThread; i++)
                counter.TryIncrementIfBelow(cap);
        });

        Assert.True(counter.Value <= cap, $"Counter {counter.Value} exceeded cap {cap}.");
        Assert.Equal(cap, counter.Value); // All permits should be consumed.
    }
}
