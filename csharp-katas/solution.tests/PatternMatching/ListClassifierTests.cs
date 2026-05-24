namespace Katas.Tests.PatternMatching;

using Katas.PatternMatching;

public sealed class ListClassifierTests
{
    // -------------------------------------------------------------------------
    // Classify — shape-based
    // -------------------------------------------------------------------------

    [Fact]
    public void Classify_Should_ReturnEmpty_ForZeroElements()
    {
        Assert.Equal("empty", ListClassifier.Classify([]));
    }

    [Fact]
    public void Classify_Should_ReturnSingle_ForOneElement()
    {
        var result = ListClassifier.Classify([42]);
        Assert.Equal("single: 42", result);
    }

    [Fact]
    public void Classify_Should_ReturnPair_ForTwoElements()
    {
        var result = ListClassifier.Classify([1, 2]);
        Assert.Equal("pair: 1, 2", result);
    }

    [Fact]
    public void Classify_Should_ReturnMany_ForThreeOrMoreElements()
    {
        var result = ListClassifier.Classify([10, 20, 30]);

        Assert.Contains("many:", result);
        Assert.Contains("first=10", result);
        Assert.Contains("last=30", result);
        Assert.Contains("count=3", result);
    }

    [Fact]
    public void Classify_Should_ReportCorrectFirstAndLast_ForLargeArray()
    {
        var items = new[] { 1, 2, 3, 4, 5 };
        var result = ListClassifier.Classify(items);

        Assert.Contains("first=1", result);
        Assert.Contains("last=5",  result);
        Assert.Contains("count=5", result);
    }

    [Fact]
    public void Classify_Should_Throw_WhenArrayIsNull()
    {
        Assert.Throws<ArgumentNullException>(() => ListClassifier.Classify(null!));
    }

    // -------------------------------------------------------------------------
    // SumFirstAndLast — slice pattern
    // -------------------------------------------------------------------------

    [Fact]
    public void SumFirstAndLast_Should_ReturnZero_ForEmptyArray()
    {
        Assert.Equal(0, ListClassifier.SumFirstAndLast([]));
    }

    [Fact]
    public void SumFirstAndLast_Should_ReturnDoubledValue_ForSingleElement()
    {
        // single element: first == last => value + value
        Assert.Equal(10, ListClassifier.SumFirstAndLast([5]));
    }

    [Fact]
    public void SumFirstAndLast_Should_SumFirstAndLastElement_ForTwoElements()
    {
        Assert.Equal(7, ListClassifier.SumFirstAndLast([3, 4]));
    }

    [Fact]
    public void SumFirstAndLast_Should_SumFirstAndLastElement_IgnoringMiddle()
    {
        // [1, 99, 99, 99, 9] => 1 + 9 = 10
        Assert.Equal(10, ListClassifier.SumFirstAndLast([1, 99, 99, 99, 9]));
    }

    [Fact]
    public void SumFirstAndLast_Should_Throw_WhenArrayIsNull()
    {
        Assert.Throws<ArgumentNullException>(() => ListClassifier.SumFirstAndLast(null!));
    }
}
