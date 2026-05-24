namespace Katas.Tests.Spans;

using Katas.Spans;

public sealed class SpanParsingTests
{
    // -------------------------------------------------------------------------
    // TryParseInt — valid inputs
    // -------------------------------------------------------------------------

    [Theory]
    [InlineData("0",    0)]
    [InlineData("1",    1)]
    [InlineData("42",   42)]
    [InlineData("2147483647", int.MaxValue)]
    public void TryParseInt_Should_ParsePositiveIntegers(string input, int expected)
    {
        var ok = SpanParsing.TryParseInt(input.AsSpan(), out var value);

        Assert.True(ok);
        Assert.Equal(expected, value);
    }

    [Theory]
    [InlineData("-1",           -1)]
    [InlineData("-42",          -42)]
    [InlineData("-2147483648",  int.MinValue)]
    public void TryParseInt_Should_ParseNegativeIntegers(string input, int expected)
    {
        var ok = SpanParsing.TryParseInt(input.AsSpan(), out var value);

        Assert.True(ok);
        Assert.Equal(expected, value);
    }

    [Fact]
    public void TryParseInt_Should_AcceptLeadingPlusSign()
    {
        var ok = SpanParsing.TryParseInt("+7".AsSpan(), out var value);

        Assert.True(ok);
        Assert.Equal(7, value);
    }

    // -------------------------------------------------------------------------
    // TryParseInt — invalid inputs
    // -------------------------------------------------------------------------

    [Theory]
    [InlineData("")]
    [InlineData("abc")]
    [InlineData("12.3")]
    [InlineData("  42")]
    [InlineData("-")]
    [InlineData("+")]
    public void TryParseInt_Should_ReturnFalse_ForInvalidInput(string input)
    {
        var ok = SpanParsing.TryParseInt(input.AsSpan(), out _);
        Assert.False(ok);
    }

    [Fact]
    public void TryParseInt_Should_ReturnFalse_ForOverflow()
    {
        // One beyond int.MaxValue
        var ok = SpanParsing.TryParseInt("2147483648".AsSpan(), out _);
        Assert.False(ok);
    }

    // -------------------------------------------------------------------------
    // SumCsvInts
    // -------------------------------------------------------------------------

    [Fact]
    public void SumCsvInts_Should_SumAllValues_ForTypicalInput()
    {
        Assert.Equal(6, SpanParsing.SumCsvInts("1,2,3".AsSpan()));
    }

    [Fact]
    public void SumCsvInts_Should_HandleNegativeValues()
    {
        Assert.Equal(0, SpanParsing.SumCsvInts("5,-3,-2".AsSpan()));
    }

    [Fact]
    public void SumCsvInts_Should_ReturnZero_ForEmptySpan()
    {
        Assert.Equal(0, SpanParsing.SumCsvInts(ReadOnlySpan<char>.Empty));
    }

    [Fact]
    public void SumCsvInts_Should_HandleSingleValue()
    {
        Assert.Equal(42, SpanParsing.SumCsvInts("42".AsSpan()));
    }

    [Fact]
    public void SumCsvInts_Should_TreatUnparseableTokensAsZero()
    {
        // "1,abc,3" => 1 + 0 + 3 = 4
        Assert.Equal(4, SpanParsing.SumCsvInts("1,abc,3".AsSpan()));
    }

    [Fact]
    public void SumCsvInts_Should_HandleSpacesAroundValues()
    {
        // Trim is applied to each token
        Assert.Equal(10, SpanParsing.SumCsvInts(" 3 , 7 ".AsSpan()));
    }

    // -------------------------------------------------------------------------
    // CountWords
    // -------------------------------------------------------------------------

    [Fact]
    public void CountWords_Should_ReturnZero_ForEmptySpan()
    {
        Assert.Equal(0, SpanParsing.CountWords(ReadOnlySpan<char>.Empty));
    }

    [Fact]
    public void CountWords_Should_ReturnZero_ForWhitespaceOnly()
    {
        Assert.Equal(0, SpanParsing.CountWords("   ".AsSpan()));
    }

    [Fact]
    public void CountWords_Should_CountSingleWord()
    {
        Assert.Equal(1, SpanParsing.CountWords("hello".AsSpan()));
    }

    [Fact]
    public void CountWords_Should_CountMultipleWords()
    {
        Assert.Equal(3, SpanParsing.CountWords("one two three".AsSpan()));
    }

    [Fact]
    public void CountWords_Should_IgnoreLeadingAndTrailingSpaces()
    {
        Assert.Equal(2, SpanParsing.CountWords("  foo bar  ".AsSpan()));
    }

    [Fact]
    public void CountWords_Should_TreatConsecutiveSpacesAsSingleDelimiter()
    {
        Assert.Equal(2, SpanParsing.CountWords("foo  bar".AsSpan()));
    }
}
