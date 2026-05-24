namespace Katas.PatternMatching;

/// <summary>
/// Abstract base of the expression AST. All concrete nodes are sealed records,
/// forming a closed (exhaustive) hierarchy.
/// </summary>
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
/// <param name="Right">Divisor. Evaluating to zero throws <see cref="DivideByZeroException"/>.</param>
public sealed record Div(Expr Left, Expr Right) : Expr;

/// <summary>Unary negation.</summary>
/// <param name="Operand">The expression to negate.</param>
public sealed record Neg(Expr Operand) : Expr;
