namespace Katas.Tests.Nullable;

using Katas.Nullable;

public sealed class SafeParsingTests
{
    // -------------------------------------------------------------------------
    // TryGetValue — NotNullWhen(true) behaviour
    // -------------------------------------------------------------------------

    [Fact]
    public void TryGetValue_Should_ReturnTrueAndNonNullValue_WhenKeyExists()
    {
        var map = new Dictionary<string, string> { ["key"] = "value" };

        bool found = SafeParsing.TryGetValue(map, "key", out string? val);

        Assert.True(found);
        // val is non-null here: [NotNullWhen(true)] guarantees this when 'found' is true.
        Assert.Equal("value", val);
    }

    [Fact]
    public void TryGetValue_Should_ReturnFalse_WhenKeyMissing()
    {
        var map = new Dictionary<string, string>();

        bool found = SafeParsing.TryGetValue(map, "missing", out string? val);

        Assert.False(found);
        Assert.Null(val);
    }

    [Fact]
    public void TryGetValue_Should_AllowNullFreeUsage_InTrueBranch()
    {
        // This test asserts that the compiler's understanding is correct at runtime too:
        // when true, the value must not be null.
        var map = new Dictionary<string, string> { ["x"] = "hello" };

        if (SafeParsing.TryGetValue(map, "x", out string? v))
        {
            // Inside this branch, the compiler (and runtime) guarantees v is non-null.
            Assert.Equal(5, v.Length);
        }
    }

    // -------------------------------------------------------------------------
    // FirstNonNullOrDefault
    // -------------------------------------------------------------------------

    [Fact]
    public void FirstNonNullOrDefault_Should_ReturnFirstNonNullCandidate()
    {
        string result = SafeParsing.FirstNonNullOrDefault("fallback", null, null, "found", "second");
        Assert.Equal("found", result);
    }

    [Fact]
    public void FirstNonNullOrDefault_Should_ReturnFallback_WhenAllCandidatesNull()
    {
        string result = SafeParsing.FirstNonNullOrDefault("fallback", null, null);
        Assert.Equal("fallback", result);
    }

    [Fact]
    public void FirstNonNullOrDefault_Should_ReturnFallback_WhenNoCandidatesProvided()
    {
        string result = SafeParsing.FirstNonNullOrDefault("fallback");
        Assert.Equal("fallback", result);
    }

    // -------------------------------------------------------------------------
    // LengthOrZero — null-flow
    // -------------------------------------------------------------------------

    [Fact]
    public void LengthOrZero_Should_ReturnLength_WhenStringIsNotNull()
    {
        Assert.Equal(5, SafeParsing.LengthOrZero("hello"));
    }

    [Fact]
    public void LengthOrZero_Should_ReturnZero_WhenStringIsNull()
    {
        Assert.Equal(0, SafeParsing.LengthOrZero(null));
    }

    [Fact]
    public void LengthOrZero_Should_ReturnZero_ForEmptyString()
    {
        Assert.Equal(0, SafeParsing.LengthOrZero(""));
    }

    // -------------------------------------------------------------------------
    // Combine — always returns non-null
    // -------------------------------------------------------------------------

    [Fact]
    public void Combine_Should_ConcatenateNonNullStrings()
    {
        string result = SafeParsing.Combine("hello", " world");
        Assert.Equal("hello world", result);
    }

    [Fact]
    public void Combine_Should_TreatNullAsEmpty_ForFirstArg()
    {
        string result = SafeParsing.Combine(null, "world");
        Assert.Equal("world", result);
        Assert.NotNull(result);
    }

    [Fact]
    public void Combine_Should_TreatNullAsEmpty_ForSecondArg()
    {
        string result = SafeParsing.Combine("hello", null);
        Assert.Equal("hello", result);
        Assert.NotNull(result);
    }

    [Fact]
    public void Combine_Should_ReturnEmptyString_WhenBothNull()
    {
        string result = SafeParsing.Combine(null, null);
        Assert.Equal("", result);
        Assert.NotNull(result);
    }
}
