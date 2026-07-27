namespace Katas.FeedParser;

using System.Globalization;

/// <summary>
/// The category of a malformed feed line. Validation short-circuits in a fixed
/// order (field-count → empty-symbol → bid → ask → qty), so at most one
/// <see cref="ErrorKind"/> is reported per line — the first rule that fails.
/// </summary>
public enum ErrorKind
{
    /// <summary>The line did not contain exactly four <c>|</c>-separated fields.</summary>
    WrongFieldCount,

    /// <summary>The symbol (first field) was empty.</summary>
    EmptySymbol,

    /// <summary>The bid (second field) was not a valid <see cref="double"/>.</summary>
    InvalidBid,

    /// <summary>The ask (third field) was not a valid <see cref="double"/>.</summary>
    InvalidAsk,

    /// <summary>The quantity (fourth field) was not a non-negative integer.</summary>
    InvalidQty,
}

/// <summary>
/// A parsed market-data quote: <c>SYMBOL|BID|ASK|QTY</c>.
/// </summary>
/// <param name="Symbol">The instrument identifier (the only field that allocates a string).</param>
/// <param name="Bid">The best bid price.</param>
/// <param name="Ask">The best ask price.</param>
/// <param name="Qty">The available size; always non-negative, hence <see cref="ulong"/>.</param>
public readonly record struct Quote(string Symbol, double Bid, double Ask, ulong Qty);

/// <summary>
/// A malformed line, tagged with its 1-based physical line number so an operator
/// can jump straight to the offending record in the raw feed.
/// </summary>
/// <param name="Line">The 1-based physical line number (blank/comment lines still count).</param>
/// <param name="Kind">Which validation rule failed.</param>
public readonly record struct ParseError(int Line, ErrorKind Kind);

/// <summary>
/// The outcome of one non-skipped feed line: exactly one of <see cref="Quote"/> or
/// <see cref="Error"/> is populated.
/// </summary>
/// <param name="Line">The 1-based physical line number this outcome came from.</param>
/// <param name="Quote">The parsed quote, or <c>null</c> if the line was malformed.</param>
/// <param name="Error">The parse error, or <c>null</c> if the line was a valid quote.</param>
public readonly record struct ParsedLine(int Line, Quote? Quote, ParseError? Error);

/// <summary>
/// A streaming, allocation-conscious parser for a pipe-delimited market-data feed
/// (<c>SYMBOL|BID|ASK|QTY</c> per line).
/// </summary>
/// <remarks>
/// <para>
/// <b>The zero-allocation story.</b> Each line is parsed through a
/// <see cref="ReadOnlySpan{T}"/> view over the source string. The four fields are
/// sliced with <see cref="MemoryExtensions.Split(ReadOnlySpan{char}, Span{Range}, char, StringSplitOptions)"/>,
/// which writes <see cref="Range"/> markers into a <c>stackalloc</c> buffer rather than
/// materialising a <c>string[]</c>. Numbers are parsed straight off the span via the
/// span-based <c>double.TryParse</c> / <c>ulong.TryParse</c> overloads, so no
/// intermediate substrings are allocated. The <em>only</em> heap allocation on the happy
/// path is the symbol string, produced with <c>ToString()</c> at the very end — because a
/// <see cref="Quote"/> must outlive the stack frame and a span cannot.
/// </para>
/// <para>
/// <b>TryParse over exceptions.</b> Malformed numbers are an expected, high-frequency
/// condition in a real feed. Exceptions for control flow would be catastrophic for
/// throughput, so we use the <c>TryParse</c> family and turn a <c>false</c> return into a
/// typed <see cref="ParseError"/>. A leading <c>'-'</c> makes <c>ulong.TryParse</c> return
/// <c>false</c>, which is exactly how the "quantity must be non-negative" rule is enforced.
/// </para>
/// <para>
/// <b>Streaming.</b> <see cref="Parse"/> is a lazy iterator (<c>yield return</c>): it pulls
/// one line at a time from the source <see cref="IEnumerable{T}"/> and emits one
/// <see cref="ParsedLine"/> per non-skipped line, so an unbounded feed can be processed with
/// O(1) memory. Because a <c>ref struct</c> span cannot live across a <c>yield</c>, the
/// per-line span work is isolated in a non-iterator helper that returns a plain value type.
/// </para>
/// <para>
/// <b>Line-numbered errors.</b> Every <see cref="ParseError"/> carries the 1-based
/// <em>physical</em> line number (blank and comment lines are counted even though they are
/// skipped), so diagnostics point at the exact source row.
/// </para>
/// <para>
/// <b>Invariant culture.</b> Prices and quantities are always parsed with
/// <see cref="CultureInfo.InvariantCulture"/> — a feed uses <c>'.'</c> as the decimal point
/// regardless of the host machine's locale, so parsing must never depend on ambient culture.
/// </para>
/// </remarks>
public static class FeedParser
{
    private const NumberStyles PriceStyles = NumberStyles.Float | NumberStyles.AllowThousands;
    private const NumberStyles QtyStyles = NumberStyles.Integer;

    /// <summary>
    /// Lazily parses <paramref name="lines"/>, yielding one <see cref="ParsedLine"/> for each
    /// line that is neither blank nor a comment.
    /// </summary>
    /// <param name="lines">The feed, one physical record per element.</param>
    /// <returns>
    /// A lazy sequence of parse outcomes. Blank lines and lines that (after trimming) start
    /// with <c>'#'</c> are skipped entirely — they produce no element but still advance the
    /// physical line counter.
    /// </returns>
    public static IEnumerable<ParsedLine> Parse(IEnumerable<string> lines)
    {
        var lineNumber = 0;
        foreach (var line in lines)
        {
            lineNumber++;
            if (ParseLine(line, lineNumber) is { } parsed)
            {
                yield return parsed;
            }
        }
    }

    /// <summary>
    /// Eagerly parses <paramref name="lines"/>, partitioning the outcomes into the successful
    /// quotes and the errors.
    /// </summary>
    /// <param name="lines">The feed, one physical record per element.</param>
    /// <returns>
    /// A tuple of (quotes, errors) in physical-line order. Convenience over
    /// <see cref="Parse"/> for callers that want the whole feed materialised.
    /// </returns>
    public static (IReadOnlyList<Quote> Quotes, IReadOnlyList<ParseError> Errors) ParseAll(IEnumerable<string> lines)
    {
        var quotes = new List<Quote>();
        var errors = new List<ParseError>();

        foreach (var parsed in Parse(lines))
        {
            if (parsed.Quote is { } quote)
            {
                quotes.Add(quote);
            }
            else if (parsed.Error is { } error)
            {
                errors.Add(error);
            }
        }

        return (quotes, errors);
    }

    /// <summary>
    /// Parses a single physical line into a <see cref="ParsedLine"/>, or <c>null</c> when the
    /// line is skipped (blank or comment). All span work is confined here so that
    /// <see cref="Parse"/> can remain an iterator.
    /// </summary>
    private static ParsedLine? ParseLine(string line, int lineNumber)
    {
        var span = line.AsSpan().Trim();

        // Blank or comment lines are skipped — not a record, not an error.
        if (span.IsEmpty || span[0] == '#')
        {
            return null;
        }

        // A buffer of 5 ranges lets us distinguish "exactly 4" from "5 or more":
        // Split fills at most destination.Length ranges, packing any overflow into the last.
        Span<Range> ranges = stackalloc Range[5];
        var count = span.Split(ranges, '|');
        if (count != 4)
        {
            return Error(lineNumber, ErrorKind.WrongFieldCount);
        }

        var symbol = span[ranges[0]];
        if (symbol.IsEmpty)
        {
            return Error(lineNumber, ErrorKind.EmptySymbol);
        }

        if (!double.TryParse(span[ranges[1]], PriceStyles, CultureInfo.InvariantCulture, out var bid))
        {
            return Error(lineNumber, ErrorKind.InvalidBid);
        }

        if (!double.TryParse(span[ranges[2]], PriceStyles, CultureInfo.InvariantCulture, out var ask))
        {
            return Error(lineNumber, ErrorKind.InvalidAsk);
        }

        // ulong.TryParse rejects a leading '-', enforcing the "non-negative" rule for free.
        if (!ulong.TryParse(span[ranges[3]], QtyStyles, CultureInfo.InvariantCulture, out var qty))
        {
            return Error(lineNumber, ErrorKind.InvalidQty);
        }

        // Only now do we allocate — the symbol string must outlive the stack frame.
        return new ParsedLine(lineNumber, new Quote(symbol.ToString(), bid, ask, qty), null);
    }

    private static ParsedLine Error(int lineNumber, ErrorKind kind) =>
        new(lineNumber, null, new ParseError(lineNumber, kind));
}
