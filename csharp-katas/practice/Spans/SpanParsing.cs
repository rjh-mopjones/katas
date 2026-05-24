namespace Katas.Spans;

/// <summary>
/// Demonstrates zero-allocation parsing and processing using
/// <see cref="ReadOnlySpan{T}"/> and <c>stackalloc</c>.
/// </summary>
public static class SpanParsing
{
    /// <summary>
    /// Parses an optionally-signed decimal integer from a character span without
    /// any heap allocations.
    /// </summary>
    public static bool TryParseInt(ReadOnlySpan<char> s, out int value)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    /// Sums comma-separated signed integers found in <paramref name="line"/>
    /// without allocating any strings.
    /// </summary>
    public static int SumCsvInts(ReadOnlySpan<char> line)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    /// Counts the number of whitespace-delimited words in <paramref name="text"/>.
    /// Consecutive whitespace counts as a single delimiter; leading/trailing whitespace
    /// does not produce empty words.
    /// </summary>
    public static int CountWords(ReadOnlySpan<char> text)
    {
        throw new NotImplementedException();
    }
}
