using Katas.Iterators;

namespace Katas.Tests.Iterators;

public class CountdownSequenceTests
{
    [Fact]
    public void CountdownSequence_Should_EmitValuesFromDownToOne()
    {
        var seq = new CountdownSequence(5);
        Assert.Equal([5, 4, 3, 2, 1], seq.ToList());
    }

    [Fact]
    public void CountdownSequence_Should_EmitSingleValueWhenFromIsOne()
    {
        var seq = new CountdownSequence(1);
        Assert.Equal([1], seq.ToList());
    }

    [Fact]
    public void CountdownSequence_Should_YieldNothingWhenFromIsZero()
    {
        var seq = new CountdownSequence(0);
        Assert.Empty(seq.ToList());
    }

    [Fact]
    public void CountdownSequence_Should_YieldNothingWhenFromIsNegative()
    {
        var seq = new CountdownSequence(-3);
        Assert.Empty(seq.ToList());
    }

    [Fact]
    public void CountdownSequence_Should_SupportResetAndReEnumeration()
    {
        var seq = new CountdownSequence(3);

        // First enumeration
        Assert.Equal([3, 2, 1], seq.ToList());

        // Obtain a fresh enumerator (second foreach call on the same object)
        Assert.Equal([3, 2, 1], seq.ToList());
    }

    [Fact]
    public void CountdownSequence_Should_SupportManualResetOnEnumerator()
    {
        var seq = new CountdownSequence(3);
        var enumerator = seq.GetEnumerator();

        // Consume fully
        while (enumerator.MoveNext()) { }

        // Reset and re-consume
        enumerator.Reset();
        var values = new List<int>();
        while (enumerator.MoveNext()) values.Add(enumerator.Current);
        enumerator.Dispose();

        Assert.Equal([3, 2, 1], values);
    }

    [Fact]
    public void CountdownSequence_Should_ProduceTwoIndependentEnumeratorsWithoutInterference()
    {
        var seq = new CountdownSequence(4);

        // Start two enumerators and interleave MoveNext calls.
        using var e1 = seq.GetEnumerator();
        using var e2 = seq.GetEnumerator();

        // Advance e1 twice
        Assert.True(e1.MoveNext()); Assert.Equal(4, e1.Current);
        Assert.True(e1.MoveNext()); Assert.Equal(3, e1.Current);

        // e2 should still be at the start
        Assert.True(e2.MoveNext()); Assert.Equal(4, e2.Current);

        // e1 continues independently
        Assert.True(e1.MoveNext()); Assert.Equal(2, e1.Current);

        // Consume the rest of e2
        Assert.True(e2.MoveNext()); Assert.Equal(3, e2.Current);
        Assert.True(e2.MoveNext()); Assert.Equal(2, e2.Current);
        Assert.True(e2.MoveNext()); Assert.Equal(1, e2.Current);
        Assert.False(e2.MoveNext());

        // e1 should still have remaining values
        Assert.True(e1.MoveNext()); Assert.Equal(1, e1.Current);
        Assert.False(e1.MoveNext());
    }

    [Fact]
    public void CountdownSequence_Should_ReturnFalseOnMoveNextAfterExhaustion()
    {
        var seq = new CountdownSequence(2);
        using var e = seq.GetEnumerator();

        e.MoveNext(); // 2
        e.MoveNext(); // 1
        Assert.False(e.MoveNext());
        Assert.False(e.MoveNext()); // idempotent
    }

    [Fact]
    public void CountdownSequence_Should_ReturnFalseOnMoveNextAfterDispose()
    {
        var seq = new CountdownSequence(3);
        var e = seq.GetEnumerator();
        e.MoveNext();
        e.Dispose();
        Assert.False(e.MoveNext());
    }
}
