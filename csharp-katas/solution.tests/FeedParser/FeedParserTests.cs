namespace Katas.Tests.FeedParser;

using Katas.FeedParser;

public sealed class FeedParserTests
{
    // The canonical sample feed — shared across all six language ports.
    private static readonly string[] CanonicalFeed =
    [
        "# market data feed",
        "LIV-MUN|1.95|2.05|1000",
        "",
        "ARS-CHE|1.50|1.60|500",
        "|1.0|2.0|10",
        "BAD|x|2.0|10",
        "TOO|1.0|2.0",
        "NEG|1.0|2.0|-5",
    ];

    // -------------------------------------------------------------------------
    // Canonical feed — the contract
    // -------------------------------------------------------------------------

    [Fact]
    public void ParseAll_Should_ReturnExactQuotes_ForCanonicalFeed()
    {
        var (quotes, _) = FeedParser.ParseAll(CanonicalFeed);

        Assert.Equal(
            new[]
            {
                new Quote("LIV-MUN", 1.95, 2.05, 1000),
                new Quote("ARS-CHE", 1.50, 1.60, 500),
            },
            quotes);
    }

    [Fact]
    public void ParseAll_Should_ReturnExactLineNumberedErrors_ForCanonicalFeed()
    {
        var (_, errors) = FeedParser.ParseAll(CanonicalFeed);

        Assert.Equal(
            new[]
            {
                new ParseError(5, ErrorKind.EmptySymbol),
                new ParseError(6, ErrorKind.InvalidBid),
                new ParseError(7, ErrorKind.WrongFieldCount),
                new ParseError(8, ErrorKind.InvalidQty),
            },
            errors);
    }

    // -------------------------------------------------------------------------
    // Individual error variants
    // -------------------------------------------------------------------------

    [Theory]
    [InlineData("A|1.0|2.0")]          // too few fields
    [InlineData("A|1.0|2.0|10|extra")] // too many fields
    [InlineData("no-pipes-at-all")]
    public void ParseAll_Should_FlagWrongFieldCount(string line)
    {
        var (quotes, errors) = FeedParser.ParseAll([line]);

        Assert.Empty(quotes);
        Assert.Equal(new ParseError(1, ErrorKind.WrongFieldCount), Assert.Single(errors));
    }

    [Fact]
    public void ParseAll_Should_FlagEmptySymbol()
    {
        var (quotes, errors) = FeedParser.ParseAll(["|1.0|2.0|10"]);

        Assert.Empty(quotes);
        Assert.Equal(new ParseError(1, ErrorKind.EmptySymbol), Assert.Single(errors));
    }

    [Fact]
    public void ParseAll_Should_FlagInvalidBid()
    {
        var (quotes, errors) = FeedParser.ParseAll(["A|x|2.0|10"]);

        Assert.Empty(quotes);
        Assert.Equal(new ParseError(1, ErrorKind.InvalidBid), Assert.Single(errors));
    }

    [Fact]
    public void ParseAll_Should_FlagInvalidAsk()
    {
        var (quotes, errors) = FeedParser.ParseAll(["A|1.0|y|10"]);

        Assert.Empty(quotes);
        Assert.Equal(new ParseError(1, ErrorKind.InvalidAsk), Assert.Single(errors));
    }

    [Theory]
    [InlineData("A|1.0|2.0|-5")] // negative
    [InlineData("A|1.0|2.0|1.5")] // not an integer
    [InlineData("A|1.0|2.0|abc")] // not a number
    public void ParseAll_Should_FlagInvalidQty(string line)
    {
        var (quotes, errors) = FeedParser.ParseAll([line]);

        Assert.Empty(quotes);
        Assert.Equal(new ParseError(1, ErrorKind.InvalidQty), Assert.Single(errors));
    }

    [Fact]
    public void ParseAll_Should_StopAtFirstFailingRule_InFixedOrder()
    {
        // Empty symbol AND a bad bid — empty-symbol wins because it is checked first.
        var (_, errors) = FeedParser.ParseAll(["|x|2.0|10"]);

        Assert.Equal(ErrorKind.EmptySymbol, Assert.Single(errors).Kind);
    }

    // -------------------------------------------------------------------------
    // Skipping & edge cases
    // -------------------------------------------------------------------------

    [Fact]
    public void ParseAll_Should_ReturnNothing_ForEmptyInput()
    {
        var (quotes, errors) = FeedParser.ParseAll([]);

        Assert.Empty(quotes);
        Assert.Empty(errors);
    }

    [Fact]
    public void ParseAll_Should_SkipBlankAndCommentLines_WithoutEmitting()
    {
        var (quotes, errors) = FeedParser.ParseAll(["", "   ", "# a comment", "\t# indented comment"]);

        Assert.Empty(quotes);
        Assert.Empty(errors);
    }

    [Fact]
    public void Parse_Should_SkippedLinesStillAdvancePhysicalLineNumber()
    {
        // Line 1 blank, line 2 comment, line 3 the error → error must report line 3.
        var (_, errors) = FeedParser.ParseAll(["", "# skip me", "|1.0|2.0|10"]);

        Assert.Equal(3, Assert.Single(errors).Line);
    }

    [Fact]
    public void Parse_Should_YieldOneParsedLinePerNonSkippedLine()
    {
        var parsed = FeedParser.Parse(CanonicalFeed).ToList();

        // 8 physical lines, 2 skipped (comment + blank) → 6 emitted.
        Assert.Equal(6, parsed.Count);
        Assert.Equal(2, parsed.Count(p => p.Quote is not null));
        Assert.Equal(4, parsed.Count(p => p.Error is not null));
    }

    [Fact]
    public void Parse_Should_BeLazy_AndNotThrow_OnUnterminatedSource()
    {
        // Taking only the first element must not force the whole (infinite) sequence.
        var first = FeedParser.Parse(InfiniteFeed()).First();

        Assert.Equal(new Quote("A", 1.0, 2.0, 1), first.Quote);
        return;

        static IEnumerable<string> InfiniteFeed()
        {
            while (true)
            {
                yield return "A|1.0|2.0|1";
            }
        }
    }

    [Fact]
    public void Parse_Should_TrimSurroundingWhitespace_BeforeParsing()
    {
        var (quotes, errors) = FeedParser.ParseAll(["  LIV-MUN|1.95|2.05|1000  "]);

        Assert.Empty(errors);
        Assert.Equal(new Quote("LIV-MUN", 1.95, 2.05, 1000), Assert.Single(quotes));
    }
}
