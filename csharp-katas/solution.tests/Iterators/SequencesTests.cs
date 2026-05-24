using Katas.Iterators;

namespace Katas.Tests.Iterators;

public class SequencesTests
{
    // =========================================================================
    // Fibonacci
    // =========================================================================

    [Fact]
    public void Fibonacci_Should_MatchKnownFirstTenValues()
    {
        var expected = new long[] { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 };
        var actual = Sequences.Fibonacci().Take(10).ToArray();
        Assert.Equal(expected, actual);
    }

    [Fact]
    public void Fibonacci_Should_BeInfiniteAndSafeWithTake()
    {
        // If the sequence were finite this would throw or return fewer than 50 items.
        var taken = Sequences.Fibonacci().Take(50).ToList();
        Assert.Equal(50, taken.Count);
    }

    [Fact]
    public void Fibonacci_Should_StartsWithZero()
    {
        Assert.Equal(0L, Sequences.Fibonacci().First());
    }

    [Fact]
    public void Fibonacci_Should_EachValueEqualsTheSumOfPreviousTwo()
    {
        // Property-based style: verify the Fibonacci recurrence for the first 20 terms.
        var fibs = Sequences.Fibonacci().Take(22).ToList();
        for (var i = 2; i < fibs.Count; i++)
            Assert.Equal(fibs[i - 2] + fibs[i - 1], fibs[i]);
    }

    [Fact]
    public void Fibonacci_Should_ProduceTwoIndependentEnumeratorsWithoutInterference()
    {
        // Two separate Take calls should each start from 0 independently.
        var first5a = Sequences.Fibonacci().Take(5).ToList();
        var first5b = Sequences.Fibonacci().Take(5).ToList();
        Assert.Equal(first5a, first5b);
    }

    // =========================================================================
    // Naturals
    // =========================================================================

    [Fact]
    public void Naturals_Should_StartAtOne()
    {
        Assert.Equal(1, Sequences.Naturals().First());
    }

    [Fact]
    public void Naturals_Should_ProduceConsecutiveIntegersFromOne()
    {
        var expected = Enumerable.Range(1, 20).ToList();
        var actual = Sequences.Naturals().Take(20).ToList();
        Assert.Equal(expected, actual);
    }

    [Fact]
    public void Naturals_Should_BeInfiniteAndSafeWithTake()
    {
        var taken = Sequences.Naturals().Take(1000).ToList();
        Assert.Equal(1000, taken.Count);
    }

    // =========================================================================
    // Cycle
    // =========================================================================

    [Fact]
    public void Cycle_Should_RepeatItemsInOrder()
    {
        var items = new[] { 1, 2, 3 };
        var cycled = Sequences.Cycle(items).Take(7).ToList();

        Assert.Equal([1, 2, 3, 1, 2, 3, 1], cycled);
    }

    [Fact]
    public void Cycle_Should_WorkWithSingleElementList()
    {
        var items = new[] { 42 };
        var cycled = Sequences.Cycle(items).Take(5).ToList();

        Assert.Equal([42, 42, 42, 42, 42], cycled);
    }

    [Fact]
    public void Cycle_Should_BeInfiniteAndSafeWithTake()
    {
        var items = new[] { "a", "b" };
        var taken = Sequences.Cycle(items).Take(1000).ToList();
        Assert.Equal(1000, taken.Count);
    }

    [Fact]
    public void Cycle_Should_ThrowArgumentNullExceptionForNullItems()
    {
        IReadOnlyList<int> nullItems = null!;
        Assert.Throws<ArgumentNullException>(() => Sequences.Cycle(nullItems).Take(1).ToList());
    }

    [Fact]
    public void Cycle_Should_ThrowArgumentExceptionForEmptyItems()
    {
        Assert.Throws<ArgumentException>(() => Sequences.Cycle(Array.Empty<int>()).Take(1).ToList());
    }

    [Fact]
    public void Cycle_Should_ProduceTwoIndependentEnumeratorsWithoutInterference()
    {
        var items = new[] { 10, 20 };

        // Advancing one enumerator must not affect the other.
        var e1 = Sequences.Cycle(items).GetEnumerator();
        var e2 = Sequences.Cycle(items).GetEnumerator();

        e1.MoveNext(); Assert.Equal(10, e1.Current);
        e1.MoveNext(); Assert.Equal(20, e1.Current);
        e1.MoveNext(); Assert.Equal(10, e1.Current); // wrapped

        // e2 should still be at its own start
        e2.MoveNext(); Assert.Equal(10, e2.Current);

        e1.Dispose();
        e2.Dispose();
    }
}
