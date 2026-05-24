namespace Katas.PatternMatching;

/// <summary>
/// Demonstrates C# list patterns and slice patterns on arrays.
/// </summary>
/// <remarks>
/// <para>
/// <b>List patterns (C# 11):</b>  A list pattern matches the length and, optionally,
/// specific element positions of an array or list.  Patterns are matched from left
/// to right; the compiler generates efficient length checks before element checks.
/// </para>
/// <para>
/// <b>Slice pattern (<c>..</c>):</b>  The <c>..</c> pattern matches zero or more
/// elements in the middle of a sequence.  It can optionally be bound to a name:
/// <c>[var first, .. var middle, var last]</c> captures the middle slice as a
/// sub-array.  An unbound <c>..</c> discards the middle portion.
/// </para>
/// <para>
/// <b>Exhaustiveness:</b>  The switch expression needs a <c>_</c> arm only when
/// the hierarchy of patterns does not cover every possible length.  Here the
/// final <c>[..]</c> arm catches all arrays with 3+ elements.
/// </para>
/// </remarks>
public static class ListClassifier
{
    /// <summary>
    /// Classifies an integer array by its structural shape.
    /// </summary>
    /// <param name="items">The array to classify.  Must not be <c>null</c>.</param>
    /// <returns>
    /// <list type="bullet">
    ///   <item><description><c>"empty"</c> — zero elements.</description></item>
    ///   <item><description><c>"single: N"</c> — exactly one element.</description></item>
    ///   <item><description><c>"pair: A, B"</c> — exactly two elements.</description></item>
    ///   <item>
    ///     <description>
    ///       <c>"many: first=A, last=B, count=N"</c> — three or more elements.
    ///       The slice pattern <c>[var first, .., var last]</c> extracts the first
    ///       and last elements without allocating a new array for the middle portion.
    ///     </description>
    ///   </item>
    /// </list>
    /// </returns>
    /// <exception cref="ArgumentNullException"><paramref name="items"/> is <c>null</c>.</exception>
    public static string Classify(int[] items)
    {
        if (items is null) throw new ArgumentNullException(nameof(items));

        return items switch
        {
            []                          => "empty",
            [var only]                  => $"single: {only}",
            [var a, var b]              => $"pair: {a}, {b}",
            [var first, .., var last]   => $"many: first={first}, last={last}, count={items.Length}",
        };
    }

    /// <summary>
    /// Returns the sum of the first and last elements of <paramref name="items"/>,
    /// or zero for an empty array.
    /// </summary>
    /// <param name="items">Source array.  Must not be <c>null</c>.</param>
    /// <returns>
    /// <c>first + last</c> for arrays with two or more elements;
    /// the single value doubled for a one-element array (first == last);
    /// <c>0</c> for an empty array.
    /// </returns>
    /// <exception cref="ArgumentNullException"><paramref name="items"/> is <c>null</c>.</exception>
    /// <remarks>
    /// <para>
    /// The <c>[var only]</c> arm handles the single-element case where "first" and
    /// "last" refer to the same element.  Returning <c>only + only</c> is a deliberate
    /// design choice that makes the contract self-consistent: if every element appears
    /// in both the "first" and "last" roles, it is counted twice.
    /// </para>
    /// <para>
    /// The <c>[var f, .., var l]</c> arm matches arrays of length ≥ 2; the slice <c>..</c>
    /// discards the middle without allocating.
    /// </para>
    /// </remarks>
    public static int SumFirstAndLast(int[] items)
    {
        if (items is null) throw new ArgumentNullException(nameof(items));

        return items switch
        {
            []                      => 0,
            [var only]              => only + only,
            [var first, .., var last] => first + last,
        };
    }
}
