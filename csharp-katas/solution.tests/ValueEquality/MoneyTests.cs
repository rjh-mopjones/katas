namespace Katas.Tests.ValueEquality;

using Katas.ValueEquality;

public sealed class MoneyTests
{
    // -------------------------------------------------------------------------
    // Equality and hash code
    // -------------------------------------------------------------------------

    [Fact]
    public void Equals_Should_ReturnTrue_WhenAmountAndCurrencyMatch()
    {
        var a = new Money(10m, "USD");
        var b = new Money(10m, "USD");

        Assert.Equal(a, b);
        Assert.True(a == b);
        Assert.False(a != b);
    }

    [Fact]
    public void Equals_Should_ReturnFalse_WhenAmountsDiffer()
    {
        var a = new Money(10m, "USD");
        var b = new Money(20m, "USD");

        Assert.NotEqual(a, b);
        Assert.True(a != b);
    }

    [Fact]
    public void Equals_Should_ReturnFalse_WhenCurrenciesDiffer()
    {
        var a = new Money(10m, "USD");
        var b = new Money(10m, "EUR");

        Assert.NotEqual(a, b);
    }

    [Fact]
    public void Equals_Should_BeCaseInsensitive_ForCurrencyCode()
    {
        var lower = new Money(10m, "usd");
        var upper = new Money(10m, "USD");

        Assert.Equal(lower, upper);
        Assert.Equal(lower.GetHashCode(), upper.GetHashCode());
    }

    [Fact]
    public void GetHashCode_Should_BeConsistentWithEquality()
    {
        var a = new Money(5.5m, "GBP");
        var b = new Money(5.5m, "GBP");

        Assert.Equal(a.GetHashCode(), b.GetHashCode());
    }

    // -------------------------------------------------------------------------
    // Comparison ordering
    // -------------------------------------------------------------------------

    [Fact]
    public void CompareTo_Should_OrderByAmountWithinSameCurrency()
    {
        var low  = new Money(5m,  "EUR");
        var high = new Money(10m, "EUR");

        Assert.True(low < high);
        Assert.True(high > low);
        Assert.True(low <= low);
        Assert.True(low >= low);
    }

    [Fact]
    public void CompareTo_Should_ReturnZero_ForEqualValues()
    {
        var a = new Money(3m, "USD");
        var b = new Money(3m, "USD");

        Assert.Equal(0, a.CompareTo(b));
    }

    // -------------------------------------------------------------------------
    // Arithmetic operators
    // -------------------------------------------------------------------------

    [Fact]
    public void Addition_Should_SumAmounts_WhenCurrenciesMatch()
    {
        var result = new Money(3m, "USD") + new Money(7m, "USD");

        Assert.Equal(new Money(10m, "USD"), result);
    }

    [Fact]
    public void Subtraction_Should_DifferenceAmounts_WhenCurrenciesMatch()
    {
        var result = new Money(10m, "USD") - new Money(4m, "USD");

        Assert.Equal(new Money(6m, "USD"), result);
    }

    [Fact]
    public void Multiplication_Should_ScaleAmount_PreservingCurrency()
    {
        var result = new Money(5m, "EUR") * 3m;

        Assert.Equal(new Money(15m, "EUR"), result);
    }

    [Fact]
    public void Multiplication_CommutativeOverload_Should_ProduceSameResult()
    {
        var a = new Money(4m, "GBP") * 2.5m;
        var b = 2.5m * new Money(4m, "GBP");

        Assert.Equal(a, b);
    }

    // -------------------------------------------------------------------------
    // Currency mismatch throws
    // -------------------------------------------------------------------------

    [Fact]
    public void Addition_Should_Throw_WhenCurrenciesDiffer()
    {
        var usd = new Money(10m, "USD");
        var eur = new Money(10m, "EUR");

        Assert.Throws<InvalidOperationException>(() => _ = usd + eur);
    }

    [Fact]
    public void Subtraction_Should_Throw_WhenCurrenciesDiffer()
    {
        var usd = new Money(10m, "USD");
        var eur = new Money(5m,  "EUR");

        Assert.Throws<InvalidOperationException>(() => _ = usd - eur);
    }

    [Fact]
    public void CompareTo_Should_Throw_WhenCurrenciesDiffer()
    {
        var usd = new Money(10m, "USD");
        var eur = new Money(5m,  "EUR");

        Assert.Throws<InvalidOperationException>(() => usd.CompareTo(eur));
    }

    [Fact]
    public void LessThan_Should_Throw_WhenCurrenciesDiffer()
    {
        var usd = new Money(1m, "USD");
        var eur = new Money(2m, "EUR");

        Assert.Throws<InvalidOperationException>(() => _ = usd < eur);
    }

    // -------------------------------------------------------------------------
    // operator== vs .Equals
    // -------------------------------------------------------------------------

    [Fact]
    public void OperatorEquals_And_TypedEquals_Should_Agree()
    {
        var a = new Money(10m, "USD");
        var b = new Money(10m, "USD");

        Assert.Equal(a == b, a.Equals(b));
    }

    [Fact]
    public void ObjectEquals_Should_ReturnFalse_ForNonMoneyObject()
    {
        var money = new Money(10m, "USD");
        Assert.False(money.Equals("10 USD"));
    }

    // -------------------------------------------------------------------------
    // Used as dictionary key
    // -------------------------------------------------------------------------

    [Fact]
    public void Money_Should_WorkAsDictionaryKey()
    {
        var dict = new Dictionary<Money, string>
        {
            [new Money(10m, "USD")] = "ten dollars",
        };

        // Same value with different case should find the same key
        var found = dict.TryGetValue(new Money(10m, "usd"), out var label);

        Assert.True(found);
        Assert.Equal("ten dollars", label);
    }

    // -------------------------------------------------------------------------
    // Zero factory
    // -------------------------------------------------------------------------

    [Fact]
    public void Zero_Should_ReturnMoneyWithZeroAmount()
    {
        var zero = Money.Zero("USD");

        Assert.Equal(0m, zero.Amount);
        Assert.Equal("USD", zero.Currency);
    }

    [Fact]
    public void Zero_Should_BeNeutralElementForAddition()
    {
        var ten  = new Money(10m, "USD");
        var zero = Money.Zero("USD");

        Assert.Equal(ten, ten + zero);
    }

    // -------------------------------------------------------------------------
    // ToString
    // -------------------------------------------------------------------------

    [Fact]
    public void ToString_Should_IncludeAmountAndCurrency()
    {
        var money = new Money(42.5m, "GBP");
        var text  = money.ToString();

        Assert.Contains("42.5", text);
        Assert.Contains("GBP", text);
    }
}
