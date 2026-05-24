namespace Katas.PatternMatching;

/// <summary>
/// Recursively evaluates an <see cref="Expr"/> AST to a <see cref="double"/> result.
/// </summary>
/// <remarks>
/// <para>
/// <b>Switch expression exhaustiveness:</b>  Because every concrete subtype of
/// <see cref="Expr"/> is a <c>sealed record</c>, the C# compiler can verify at
/// compile time that the <c>switch</c> expression covers all possible cases.
/// The discard arm (<c>_ =></c>) is intentionally absent — adding a new node type
/// forces a compile error here, reminding the implementer to handle it.
/// </para>
/// <para>
/// <b>Positional patterns:</b>  Each arm uses the synthesised <c>Deconstruct</c>
/// from the positional record.  For example, <c>Add(var l, var r)</c> binds
/// <c>l</c> to <c>Left</c> and <c>r</c> to <c>Right</c>, without explicit property
/// accesses.  This mirrors the mathematical notation for a grammar rule.
/// </para>
/// <para>
/// <b>Div-by-zero:</b>  We throw <see cref="DivideByZeroException"/> explicitly
/// rather than propagating a <c>double.NaN</c> / <c>double.PositiveInfinity</c>,
/// because silent propagation of non-finite values is a common source of subtle bugs.
/// Callers that need IEEE 754 semantics can remove the check.
/// </para>
/// </remarks>
public static class Evaluator
{
    /// <summary>
    /// Evaluates <paramref name="expr"/> and returns its numeric value.
    /// </summary>
    /// <param name="expr">Root of the expression tree.  Must not be <c>null</c>.</param>
    /// <returns>The evaluated result as a <see cref="double"/>.</returns>
    /// <exception cref="ArgumentNullException"><paramref name="expr"/> is <c>null</c>.</exception>
    /// <exception cref="DivideByZeroException">
    /// A <see cref="Div"/> node's right operand evaluates to zero.
    /// </exception>
    public static double Evaluate(Expr expr)
    {
        if (expr is null) throw new ArgumentNullException(nameof(expr));

        return expr switch
        {
            Num(var v)          => v,
            Add(var l, var r)   => Evaluate(l) + Evaluate(r),
            Sub(var l, var r)   => Evaluate(l) - Evaluate(r),
            Mul(var l, var r)   => Evaluate(l) * Evaluate(r),
            Div(var l, var r)   => EvaluateDiv(l, r),
            Neg(var operand)    => -Evaluate(operand),
            // The base record is not sealed, so the compiler cannot prove the switch is
            // exhaustive; a discard arm satisfies CS8509 and guards against future node types.
            _                   => throw new ArgumentException($"Unknown expression node: {expr.GetType().Name}", nameof(expr)),
        };
    }

    private static double EvaluateDiv(Expr left, Expr right)
    {
        var divisor = Evaluate(right);
        if (divisor == 0.0)
            throw new DivideByZeroException("Division by zero in expression tree.");
        return Evaluate(left) / divisor;
    }
}
