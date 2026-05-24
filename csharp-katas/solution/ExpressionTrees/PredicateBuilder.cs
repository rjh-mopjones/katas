using System.Linq.Expressions;

namespace Katas.ExpressionTrees;

/// <summary>
/// Composable builder for <see cref="Expression{TDelegate}"/> predicates.
/// </summary>
/// <remarks>
/// <para>
/// <b>Why expression trees instead of plain <c>Func</c>?</b>
/// A <c>Func&lt;T, bool&gt;</c> is opaque compiled code — you can invoke it but you cannot inspect
/// or translate it.  An <c>Expression&lt;Func&lt;T, bool&gt;&gt;</c> is a data structure (an
/// abstract syntax tree) that can be traversed, rewritten, and translated to another language
/// (e.g. SQL via Entity Framework / LINQ-to-SQL).  This is why ORM query providers require
/// expression trees rather than compiled delegates.
/// </para>
/// <para>
/// <b>Parameter rebinding:</b>  When two independently-created lambda expressions are combined
/// (<c>&amp;&amp;</c>, <c>||</c>) their parameter nodes are distinct objects even if they share
/// the same name.  Passing both as-is into <c>Expression.AndAlso</c> produces an invalid tree
/// (the right subtree still references its own parameter node).  A custom
/// <see cref="ParameterReplacingVisitor"/> rewrites every occurrence of the right-hand parameter
/// to the left-hand one, producing a single coherent tree with one shared parameter.
/// </para>
/// <para>
/// <b>Alternatives to a visitor:</b>  <c>Expression.Invoke</c> wraps one expression as an
/// invocation node (<c>a.Invoke(p)</c>) which avoids rewriting but produces an <c>Invoke</c>
/// node that some LINQ providers do not support.  The visitor approach produces a clean
/// flat tree compatible with all providers.
/// </para>
/// </remarks>
public static class PredicateBuilder
{
    /// <summary>
    /// Returns a predicate that is always <c>true</c>, serving as an identity for <see cref="And{T}"/>.
    /// </summary>
    /// <typeparam name="T">The subject type.</typeparam>
    /// <remarks>
    /// Starting with <c>True&lt;T&gt;()</c> allows you to fold a list of predicates into one:
    /// <code>
    ///   var combined = predicates.Aggregate(PredicateBuilder.True&lt;Person&gt;(),
    ///                                       PredicateBuilder.And);
    /// </code>
    /// </remarks>
    public static Expression<Func<T, bool>> True<T>()
    {
        return _ => true;
    }

    /// <summary>
    /// Combines two predicates with logical AND (<c>&amp;&amp;</c>).
    /// </summary>
    /// <typeparam name="T">The subject type.</typeparam>
    /// <param name="a">Left-hand predicate.</param>
    /// <param name="b">Right-hand predicate (its parameter is rebound to <paramref name="a"/>'s).</param>
    /// <returns>A new expression equivalent to <c>x =&gt; a(x) &amp;&amp; b(x)</c>.</returns>
    public static Expression<Func<T, bool>> And<T>(
        Expression<Func<T, bool>> a,
        Expression<Func<T, bool>> b)
    {
        ParameterExpression param = a.Parameters[0];
        Expression bodyB = ParameterReplacingVisitor.Replace(b.Parameters[0], param, b.Body);
        return Expression.Lambda<Func<T, bool>>(Expression.AndAlso(a.Body, bodyB), param);
    }

    /// <summary>
    /// Combines two predicates with logical OR (<c>||</c>).
    /// </summary>
    /// <typeparam name="T">The subject type.</typeparam>
    /// <param name="a">Left-hand predicate.</param>
    /// <param name="b">Right-hand predicate (its parameter is rebound to <paramref name="a"/>'s).</param>
    /// <returns>A new expression equivalent to <c>x =&gt; a(x) || b(x)</c>.</returns>
    public static Expression<Func<T, bool>> Or<T>(
        Expression<Func<T, bool>> a,
        Expression<Func<T, bool>> b)
    {
        ParameterExpression param = a.Parameters[0];
        Expression bodyB = ParameterReplacingVisitor.Replace(b.Parameters[0], param, b.Body);
        return Expression.Lambda<Func<T, bool>>(Expression.OrElse(a.Body, bodyB), param);
    }
}

/// <summary>
/// An <see cref="ExpressionVisitor"/> that replaces all occurrences of a specific
/// <see cref="ParameterExpression"/> node with another node.
/// </summary>
/// <remarks>
/// <para>
/// <b>ExpressionVisitor pattern:</b>  Expression trees are immutable; the visitor pattern is
/// the standard way to produce a modified copy.  <c>ExpressionVisitor.Visit</c> walks the tree
/// recursively.  Overriding <c>VisitParameter</c> intercepts every parameter node; if it matches
/// the target we return the replacement, otherwise we return the node unchanged.
/// </para>
/// <para>
/// All other node types are handled by the base class which calls the appropriate
/// <c>Visit*</c> override for each child, reassembling the tree bottom-up.
/// </para>
/// </remarks>
internal sealed class ParameterReplacingVisitor : ExpressionVisitor
{
    private readonly ParameterExpression _target;
    private readonly ParameterExpression _replacement;

    private ParameterReplacingVisitor(ParameterExpression target, ParameterExpression replacement)
    {
        _target = target;
        _replacement = replacement;
    }

    /// <summary>
    /// Rewrites <paramref name="expression"/> replacing every use of <paramref name="target"/>
    /// with <paramref name="replacement"/>.
    /// </summary>
    public static Expression Replace(
        ParameterExpression target,
        ParameterExpression replacement,
        Expression expression)
    {
        return new ParameterReplacingVisitor(target, replacement).Visit(expression);
    }

    /// <inheritdoc/>
    protected override Expression VisitParameter(ParameterExpression node)
    {
        return ReferenceEquals(node, _target) ? _replacement : base.VisitParameter(node);
    }
}
