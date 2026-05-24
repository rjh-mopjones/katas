namespace Katas.Tests.Structs;

using Katas.Structs;

public sealed class Vector2Tests
{
    // -------------------------------------------------------------------------
    // Equality and hash code
    // -------------------------------------------------------------------------

    [Fact]
    public void Equals_Should_ReturnTrue_WhenComponentsMatch()
    {
        var a = new Vector2(3.0, 4.0);
        var b = new Vector2(3.0, 4.0);

        Assert.Equal(a, b);
        Assert.True(a == b);
        Assert.False(a != b);
    }

    [Fact]
    public void Equals_Should_ReturnFalse_WhenComponentsDiffer()
    {
        var a = new Vector2(1.0, 2.0);
        var b = new Vector2(1.0, 3.0);

        Assert.NotEqual(a, b);
        Assert.True(a != b);
    }

    [Fact]
    public void GetHashCode_Should_BeConsistentWithEquality()
    {
        var a = new Vector2(5.0, -2.0);
        var b = new Vector2(5.0, -2.0);

        Assert.Equal(a.GetHashCode(), b.GetHashCode());
    }

    // -------------------------------------------------------------------------
    // Arithmetic operators
    // -------------------------------------------------------------------------

    [Fact]
    public void Addition_Should_AddComponentWise()
    {
        var result = new Vector2(1.0, 2.0) + new Vector2(3.0, 4.0);
        Assert.Equal(new Vector2(4.0, 6.0), result);
    }

    [Fact]
    public void Subtraction_Should_SubtractComponentWise()
    {
        var result = new Vector2(5.0, 7.0) - new Vector2(2.0, 3.0);
        Assert.Equal(new Vector2(3.0, 4.0), result);
    }

    [Fact]
    public void Multiplication_Should_ScaleComponents()
    {
        var result = new Vector2(2.0, -3.0) * 2.0;
        Assert.Equal(new Vector2(4.0, -6.0), result);
    }

    [Fact]
    public void Multiplication_CommutativeOverload_Should_ProduceSameResult()
    {
        var a = new Vector2(1.0, 2.0) * 3.0;
        var b = 3.0 * new Vector2(1.0, 2.0);

        Assert.Equal(a, b);
    }

    // -------------------------------------------------------------------------
    // Dot product
    // -------------------------------------------------------------------------

    [Fact]
    public void Dot_Should_ReturnCorrectScalar()
    {
        // (1,2)·(3,4) = 1*3 + 2*4 = 3 + 8 = 11
        var a = new Vector2(1.0, 2.0);
        var b = new Vector2(3.0, 4.0);

        Assert.Equal(11.0, a.Dot(b));
    }

    [Fact]
    public void Dot_Should_ReturnZero_ForPerpendicularVectors()
    {
        var a = new Vector2(1.0, 0.0);
        var b = new Vector2(0.0, 1.0);

        Assert.Equal(0.0, a.Dot(b));
    }

    // -------------------------------------------------------------------------
    // Length
    // -------------------------------------------------------------------------

    [Fact]
    public void Length_Should_Return5_For3_4Triangle()
    {
        var v = new Vector2(3.0, 4.0);
        Assert.Equal(5.0, v.Length, precision: 10);
    }

    [Fact]
    public void Length_Should_ReturnZero_ForZeroVector()
    {
        Assert.Equal(0.0, new Vector2(0.0, 0.0).Length);
    }

    // -------------------------------------------------------------------------
    // ToString
    // -------------------------------------------------------------------------

    [Fact]
    public void ToString_Should_ContainBothComponents()
    {
        var text = new Vector2(1.5, -2.5).ToString();
        Assert.Contains("1.5",  text);
        Assert.Contains("-2.5", text);
    }

    // -------------------------------------------------------------------------
    // Struct value semantics (no defensive copy for in params)
    // -------------------------------------------------------------------------

    [Fact]
    public void Dot_Should_AcceptInParameter_WithoutMutation()
    {
        var a = new Vector2(2.0, 3.0);
        var b = new Vector2(4.0, 5.0);

        // We call Dot twice; the `in` parameter ensures b is not copied or mutated
        var first  = a.Dot(in b);
        var second = a.Dot(in b);

        Assert.Equal(first, second);
    }
}
