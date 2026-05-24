namespace Katas.Iterators;

/// <summary>
/// Factory methods for lazily-evaluated, potentially infinite sequences.
/// </summary>
/// <remarks>
/// <para>
/// All methods here use <c>yield return</c> — the compiler translates each into the same
/// kind of state-machine class that <see cref="CountdownSequence"/> implements by hand.
/// This juxtaposition shows both faces of the same coin.
/// </para>
/// <para>
/// <b>Safety with infinite sequences:</b>  Never call <c>.ToList()</c>, <c>.Count()</c>, or
/// any other eager terminal operator on an infinite sequence without first bounding it with
/// <c>.Take(n)</c>, <c>.TakeWhile(…)</c>, or a similar operator.  The compiler and runtime
/// give no warning; the program will simply hang or run out of memory.
/// </para>
/// </remarks>
public static class Sequences
{
    /// <summary>
    /// Generates the infinite Fibonacci sequence: 0, 1, 1, 2, 3, 5, 8, …
    /// </summary>
    /// <returns>
    /// An infinite <see cref="IEnumerable{T}"/> of <see cref="long"/> values.
    /// Use <c>.Take(n)</c> to bound the output.
    /// </returns>
    /// <remarks>
    /// <para>
    /// <b>Why <c>long</c>?</b>  The 47th Fibonacci number (2,971,215,073) exceeds
    /// <see cref="int.MaxValue"/> (2,147,483,647).  Using <see cref="long"/> keeps the
    /// first 92 values exact before overflow.
    /// </para>
    /// <para>
    /// <b>Why yield-based?</b>  An infinite loop containing <c>yield return</c> is the
    /// clearest expression of a conceptually infinite stream.  The compiler state machine
    /// suspends execution between each <c>yield return</c>, so only one value lives in
    /// memory at a time — O(1) space regardless of how many elements are consumed.
    /// </para>
    /// <para>
    /// <b>Alternative:</b>  <c>Enumerable.Aggregate</c> over an infinite range, or a
    /// <c>ValueTask</c>-based async stream, would also work but add ceremony without benefit.
    /// </para>
    /// </remarks>
    public static IEnumerable<long> Fibonacci()
    {
        long a = 0, b = 1;
        while (true)
        {
            yield return a;
            (a, b) = (b, a + b);
        }
    }

    /// <summary>
    /// Generates the infinite sequence of natural numbers starting at 1: 1, 2, 3, …
    /// </summary>
    /// <returns>
    /// An infinite <see cref="IEnumerable{T}"/> of <see cref="int"/> values.
    /// Use <c>.Take(n)</c> to bound the output.
    /// </returns>
    /// <remarks>
    /// <para>
    /// This is equivalent to <c>Enumerable.Range(1, int.MaxValue)</c> except it truly
    /// wraps around (checked overflow is not performed) and signals its infinite intent
    /// more clearly through the method name.  For most practical purposes they are
    /// interchangeable when bounded by <c>Take</c>.
    /// </para>
    /// </remarks>
    public static IEnumerable<int> Naturals()
    {
        var n = 1;
        while (true)
            yield return n++;
    }

    /// <summary>
    /// Infinitely cycles through the elements of <paramref name="items"/>.
    /// </summary>
    /// <typeparam name="T">Element type.</typeparam>
    /// <param name="items">
    /// The collection to cycle.  Must not be <c>null</c> and must not be empty.
    /// </param>
    /// <returns>
    /// An infinite <see cref="IEnumerable{T}"/> that repeats <paramref name="items"/>
    /// in order, forever.  Use <c>.Take(n)</c> to bound the output.
    /// </returns>
    /// <exception cref="ArgumentNullException"><paramref name="items"/> is <c>null</c>.</exception>
    /// <exception cref="ArgumentException"><paramref name="items"/> is empty (cycling an empty collection would loop forever without emitting anything).</exception>
    /// <remarks>
    /// <para>
    /// <b>Why <see cref="IReadOnlyList{T}"/>?</b>  Index-based access avoids re-allocating
    /// an enumerator on every cycle, and the <c>IReadOnlyList</c> contract guarantees the
    /// count is O(1) and the items are stable.  Accepting a plain <c>IEnumerable&lt;T&gt;</c>
    /// would require materialising it first (the sequence might be single-pass), adding
    /// hidden allocation.
    /// </para>
    /// <para>
    /// <b>Trade-off:</b>  The modulo operation (<c>index % items.Count</c>) resets the index
    /// cheaply.  An alternative using <c>goto</c> or nested loops is possible but harder to read.
    /// </para>
    /// </remarks>
    public static IEnumerable<T> Cycle<T>(IReadOnlyList<T> items)
    {
        if (items is null) throw new ArgumentNullException(nameof(items));
        if (items.Count == 0) throw new ArgumentException("Cannot cycle an empty collection.", nameof(items));
        return CycleIterator(items);
    }

    private static IEnumerable<T> CycleIterator<T>(IReadOnlyList<T> items)
    {
        var index = 0;
        while (true)
        {
            yield return items[index];
            index = (index + 1) % items.Count;
        }
    }
}
