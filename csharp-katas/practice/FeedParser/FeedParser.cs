namespace Katas.FeedParser;

/// <summary>
/// The category of a malformed feed line. Validation short-circuits in a fixed
/// order (field-count → empty-symbol → bid → ask → qty), so at most one
/// <see cref="ErrorKind"/> is reported per line — the first rule that fails.
/// </summary>
public enum ErrorKind
{
    WrongFieldCount,
    EmptySymbol,
    InvalidBid,
    InvalidAsk,
    InvalidQty,
}

/// <summary>A parsed market-data quote: <c>SYMBOL|BID|ASK|QTY</c>.</summary>
public readonly record struct Quote(string Symbol, double Bid, double Ask, ulong Qty);

/// <summary>A malformed line, tagged with its 1-based physical line number.</summary>
public readonly record struct ParseError(int Line, ErrorKind Kind);

/// <summary>
/// The outcome of one non-skipped feed line: exactly one of <see cref="Quote"/> or
/// <see cref="Error"/> is populated.
/// </summary>
public readonly record struct ParsedLine(int Line, Quote? Quote, ParseError? Error);

/// <summary>
/// A streaming, allocation-conscious parser for a pipe-delimited market-data feed
/// (<c>SYMBOL|BID|ASK|QTY</c> per line).
/// </summary>
public static class FeedParser
{
    /// <summary>
    /// Lazily parses <paramref name="lines"/>, yielding one <see cref="ParsedLine"/> for each
    /// line that is neither blank nor a comment.
    /// </summary>
    public static IEnumerable<ParsedLine> Parse(IEnumerable<string> lines)
    {
        throw new NotImplementedException();
    }

    /// <summary>
    /// Eagerly parses <paramref name="lines"/>, partitioning the outcomes into the successful
    /// quotes and the errors.
    /// </summary>
    public static (IReadOnlyList<Quote> Quotes, IReadOnlyList<ParseError> Errors) ParseAll(IEnumerable<string> lines)
    {
        throw new NotImplementedException();
    }
}
