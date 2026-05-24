namespace Katas.Spans;

/// <summary>
/// Demonstrates zero-allocation parsing and processing using
/// <see cref="ReadOnlySpan{T}"/> and <c>stackalloc</c>.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why ReadOnlySpan?</b>  A <see cref="ReadOnlySpan{T}"/> is a stack-allocated
/// view over contiguous memory (array, string, or <c>stackalloc</c> buffer).  Slicing
/// a span does not copy the underlying data and does not allocate on the heap, so
/// hot-path parsing routines can operate on sub-sections of an input without generating
/// GC pressure.
/// </para>
/// <para>
/// <b>Span constraints:</b>  Spans cannot be stored in fields, captured by lambdas,
/// or used as generic type arguments (they are a <c>ref struct</c> under the hood).
/// They are strictly stack-only.  For cross-boundary or async scenarios, use
/// <see cref="Memory{T}"/> instead, which is a heap-allocated, GC-tracked reference.
/// </para>
/// </remarks>
public static class SpanParsing
{
    // -------------------------------------------------------------------------
    // TryParseInt
    // -------------------------------------------------------------------------

    /// <summary>
    /// Parses an optionally-signed decimal integer from a character span without
    /// any heap allocations.
    /// </summary>
    /// <param name="s">
    /// The span to parse.  Leading/trailing whitespace is NOT stripped; only an
    /// optional leading <c>'-'</c> or <c>'+'</c> sign is accepted.
    /// </param>
    /// <param name="value">
    /// When this method returns <c>true</c>, contains the parsed integer.
    /// When <c>false</c>, the value is undefined.
    /// </param>
    /// <returns>
    /// <c>true</c> if <paramref name="s"/> represents a valid signed integer that fits
    /// in an <see cref="int"/>; otherwise <c>false</c>.
    /// </returns>
    /// <remarks>
    /// <para>
    /// <b>No-allocation strategy:</b>  We iterate over the span character-by-character,
    /// accumulating the result as a <c>long</c> to detect overflow before narrowing to
    /// <c>int</c>.  No <c>string</c> is created, so no heap allocation occurs.
    /// </para>
    /// <para>
    /// <b>Alternative:</b>  <c>int.TryParse(ReadOnlySpan&lt;char&gt;, out int)</c>
    /// (BCL, .NET Core 2.1+) does exactly this.  This implementation exists to
    /// illustrate the pattern — in production code, prefer the BCL overload.
    /// </para>
    /// </remarks>
    public static bool TryParseInt(ReadOnlySpan<char> s, out int value)
    {
        value = 0;
        if (s.IsEmpty) return false;

        var negative = false;
        var start = 0;

        if (s[0] == '-') { negative = true; start = 1; }
        else if (s[0] == '+') { start = 1; }

        if (start >= s.Length) return false;

        long result = 0;
        for (var i = start; i < s.Length; i++)
        {
            var ch = s[i];
            if (ch < '0' || ch > '9') return false;

            result = result * 10 + (ch - '0');
            if (result > (long)int.MaxValue + 1) return false; // overflow guard
        }

        if (negative) result = -result;
        if (result < int.MinValue || result > int.MaxValue) return false;

        value = (int)result;
        return true;
    }

    // -------------------------------------------------------------------------
    // SumCsvInts
    // -------------------------------------------------------------------------

    /// <summary>
    /// Sums comma-separated signed integers found in <paramref name="line"/>
    /// without allocating any strings.
    /// </summary>
    /// <param name="line">
    /// A character span such as <c>"1,2,3"</c> or <c>"10,-5,7"</c>.
    /// Whitespace-only fields and invalid tokens are treated as zero.
    /// An empty span returns zero.
    /// </param>
    /// <returns>The arithmetic sum of all parseable integer fields.</returns>
    /// <remarks>
    /// <para>
    /// <b>Slicing strategy:</b>  <see cref="MemoryExtensions.IndexOf{T}"/> on a
    /// <see cref="ReadOnlySpan{T}"/> returns the index of the delimiter without
    /// scanning a heap-allocated string.  We keep slicing and advancing the remaining
    /// span until no more commas are found, then process the final token.
    /// </para>
    /// </remarks>
    public static int SumCsvInts(ReadOnlySpan<char> line)
    {
        if (line.IsEmpty) return 0;

        var sum = 0;
        var remaining = line;

        while (true)
        {
            var commaIndex = remaining.IndexOf(',');
            if (commaIndex < 0)
            {
                // Last (or only) field
                if (TryParseInt(remaining.Trim(), out var last)) sum += last;
                break;
            }

            var field = remaining[..commaIndex].Trim();
            if (TryParseInt(field, out var n)) sum += n;
            remaining = remaining[(commaIndex + 1)..];
        }

        return sum;
    }

    // -------------------------------------------------------------------------
    // CountWords
    // -------------------------------------------------------------------------

    /// <summary>
    /// Counts the number of whitespace-delimited words in <paramref name="text"/>.
    /// Consecutive whitespace characters count as a single delimiter; leading and
    /// trailing whitespace does not produce empty words.
    /// </summary>
    /// <param name="text">The character span to analyse.</param>
    /// <returns>
    /// The number of non-empty word tokens, or <c>0</c> for an empty / whitespace-only span.
    /// </returns>
    /// <remarks>
    /// <para>
    /// <b>Span slicing:</b>  We search for the next space character using
    /// <see cref="MemoryExtensions.IndexOf{T}"/>, grab the slice up to that index,
    /// check it is non-empty, then advance.  No <c>string.Split</c> is called,
    /// so no <c>string[]</c> is allocated.
    /// </para>
    /// <para>
    /// <b>stackalloc demo:</b>  The small scratch buffer below is allocated on the
    /// stack using <c>stackalloc</c>.  Stack allocation is appropriate here because
    /// (a) the size is small and known at compile time, and (b) the buffer does not
    /// outlive the method frame.
    ///
    /// Guidelines for safe <c>stackalloc</c> use:
    /// <list type="bullet">
    ///   <item>Keep sizes small — typically &lt;= 256 bytes to avoid stack overflow.</item>
    ///   <item>Never use a runtime variable as the size unless it is tightly bounded.</item>
    ///   <item>The span is automatically freed when the method returns; never return it.</item>
    ///   <item>Avoid <c>stackalloc</c> inside loops — the allocation is NOT freed per-iteration.</item>
    /// </list>
    /// </para>
    /// </remarks>
    public static int CountWords(ReadOnlySpan<char> text)
    {
        // Demonstrate stackalloc: a scratch buffer for a normalised single word (up to 64 chars).
        // Not strictly necessary here, but illustrates safe, bounded, stack-based storage.
        Span<char> scratch = stackalloc char[64];
        _ = scratch; // suppress "unused variable" warning — buffer is here for illustration

        if (text.IsEmpty) return 0;

        var count = 0;
        var remaining = text;

        while (!remaining.IsEmpty)
        {
            var spaceIndex = remaining.IndexOf(' ');
            if (spaceIndex < 0)
            {
                // No more spaces — the rest is the final word (if non-empty)
                if (!remaining.IsWhiteSpace()) count++;
                break;
            }

            var token = remaining[..spaceIndex];
            if (!token.IsEmpty && !token.IsWhiteSpace()) count++;
            remaining = remaining[(spaceIndex + 1)..];
        }

        return count;
    }
}
