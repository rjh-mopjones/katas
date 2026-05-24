namespace Katas.PatternMatching;

/// <summary>
/// Abstract base of the expression AST.  All concrete nodes are sealed records,
/// forming a closed (exhaustive) hierarchy that can be matched without a default arm.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why records for AST nodes?</b>  Records provide structural value equality
/// automatically, which makes tree comparison in tests trivial
/// (<c>Add(Num(1), Num(2)).Equals(Add(Num(1), Num(2)))</c> is <c>true</c>).
/// They also synthesise a readable <c>ToString</c> that mirrors the constructor,
/// which is invaluable when debugging recursive evaluators.
/// </para>
/// <para>
/// <b>Sealed hierarchy:</b>  Marking every leaf <c>sealed</c> prevents external
/// subclassing.  Combined with the <c>abstract</c> base, the compiler can warn
/// (and with some analyser settings, error) when a <c>switch</c> expression over
/// the hierarchy is not exhaustive.
/// </para>
/// <para>
/// <b>Positional patterns:</b>  Positional (deconstruction) patterns let us write
/// <c>Add(var l, var r)</c> instead of <c>Add a when ... => ...</c>, producing
/// code that reads like a mathematical definition.
/// </para>
/// </remarks>
public abstract record Expr;

/// <summary>A numeric literal.</summary>
/// <param name="Value">The literal value.</param>
public sealed record Num(double Value) : Expr;

/// <summary>Addition of two sub-expressions.</summary>
/// <param name="Left">Left operand.</param>
/// <param name="Right">Right operand.</param>
public sealed record Add(Expr Left, Expr Right) : Expr;

/// <summary>Subtraction of two sub-expressions.</summary>
/// <param name="Left">Left operand (minuend).</param>
/// <param name="Right">Right operand (subtrahend).</param>
public sealed record Sub(Expr Left, Expr Right) : Expr;

/// <summary>Multiplication of two sub-expressions.</summary>
/// <param name="Left">Left operand.</param>
/// <param name="Right">Right operand.</param>
public sealed record Mul(Expr Left, Expr Right) : Expr;

/// <summary>Division of two sub-expressions.</summary>
/// <param name="Left">Dividend.</param>
/// <param name="Right">Divisor.  Evaluating to zero throws <see cref="DivideByZeroException"/>.</param>
public sealed record Div(Expr Left, Expr Right) : Expr;

/// <summary>Unary negation.</summary>
/// <param name="Operand">The expression to negate.</param>
public sealed record Neg(Expr Operand) : Expr;
