namespace Katas.Tests.PatternMatching;

using Katas.PatternMatching;

public sealed class EvaluatorTests
{
    // -------------------------------------------------------------------------
    // Literals
    // -------------------------------------------------------------------------

    [Fact]
    public void Evaluate_Should_ReturnLiteralValue_ForNumNode()
    {
        Assert.Equal(42.0, Evaluator.Evaluate(new Num(42)));
    }

    // -------------------------------------------------------------------------
    // Basic operations
    // -------------------------------------------------------------------------

    [Fact]
    public void Evaluate_Should_AddTwoNumbers()
    {
        var expr = new Add(new Num(3), new Num(4));
        Assert.Equal(7.0, Evaluator.Evaluate(expr));
    }

    [Fact]
    public void Evaluate_Should_SubtractTwoNumbers()
    {
        var expr = new Sub(new Num(10), new Num(3));
        Assert.Equal(7.0, Evaluator.Evaluate(expr));
    }

    [Fact]
    public void Evaluate_Should_MultiplyTwoNumbers()
    {
        var expr = new Mul(new Num(6), new Num(7));
        Assert.Equal(42.0, Evaluator.Evaluate(expr));
    }

    [Fact]
    public void Evaluate_Should_DivideTwoNumbers()
    {
        var expr = new Div(new Num(10), new Num(4));
        Assert.Equal(2.5, Evaluator.Evaluate(expr));
    }

    [Fact]
    public void Evaluate_Should_NegateANumber()
    {
        var expr = new Neg(new Num(5));
        Assert.Equal(-5.0, Evaluator.Evaluate(expr));
    }

    // -------------------------------------------------------------------------
    // Nested (composite) expressions
    // -------------------------------------------------------------------------

    [Fact]
    public void Evaluate_Should_HandleDeepNesting()
    {
        // (2 + 3) * (10 - 4) = 5 * 6 = 30
        var expr = new Mul(
            new Add(new Num(2), new Num(3)),
            new Sub(new Num(10), new Num(4)));

        Assert.Equal(30.0, Evaluator.Evaluate(expr));
    }

    [Fact]
    public void Evaluate_Should_HandleNegationOfComplexExpr()
    {
        // -(3 + 7) = -10
        var expr = new Neg(new Add(new Num(3), new Num(7)));
        Assert.Equal(-10.0, Evaluator.Evaluate(expr));
    }

    [Fact]
    public void Evaluate_Should_HandleDivisionOfCompositeNumeratorAndDenominator()
    {
        // (8 + 2) / (3 - 1) = 10 / 2 = 5
        var expr = new Div(
            new Add(new Num(8), new Num(2)),
            new Sub(new Num(3), new Num(1)));

        Assert.Equal(5.0, Evaluator.Evaluate(expr));
    }

    // -------------------------------------------------------------------------
    // Division by zero
    // -------------------------------------------------------------------------

    [Fact]
    public void Evaluate_Should_Throw_WhenDivisorIsZero()
    {
        var expr = new Div(new Num(10), new Num(0));
        Assert.Throws<DivideByZeroException>(() => Evaluator.Evaluate(expr));
    }

    [Fact]
    public void Evaluate_Should_Throw_WhenDivisorExpressionEvaluatesToZero()
    {
        // 10 / (5 - 5) => 10 / 0
        var expr = new Div(new Num(10), new Sub(new Num(5), new Num(5)));
        Assert.Throws<DivideByZeroException>(() => Evaluator.Evaluate(expr));
    }

    // -------------------------------------------------------------------------
    // Null guard
    // -------------------------------------------------------------------------

    [Fact]
    public void Evaluate_Should_Throw_WhenExprIsNull()
    {
        Assert.Throws<ArgumentNullException>(() => Evaluator.Evaluate(null!));
    }

    // -------------------------------------------------------------------------
    // Record value equality (AST node identity)
    // -------------------------------------------------------------------------

    [Fact]
    public void Expr_Should_SupportValueEquality()
    {
        var a = new Add(new Num(1), new Num(2));
        var b = new Add(new Num(1), new Num(2));

        Assert.Equal(a, b);
    }
}
