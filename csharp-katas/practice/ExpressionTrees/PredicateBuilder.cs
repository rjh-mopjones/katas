using System.Linq.Expressions;

namespace Katas.ExpressionTrees;

/// <summary>
/// Composable builder for <see cref="Expression{TDelegate}"/> predicates.
/// </summary>
public static class PredicateBuilder
{
    /// <summary>Returns a predicate that is always <c>true</c>, serving as an identity for <see cref="And{T}"/>.</summary>
    public static Expression<Func<T, bool>> True<T>() => throw new NotImplementedException();

    /// <summary>Combines two predicates with logical AND (&&).</summary>
    public static Expression<Func<T, bool>> And<T>(
        Expression<Func<T, bool>> a,
        Expression<Func<T, bool>> b) => throw new NotImplementedException();

    /// <summary>Combines two predicates with logical OR (||).</summary>
    public static Expression<Func<T, bool>> Or<T>(
        Expression<Func<T, bool>> a,
        Expression<Func<T, bool>> b) => throw new NotImplementedException();
}
