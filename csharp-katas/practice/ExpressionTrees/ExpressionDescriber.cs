using System.Linq.Expressions;

namespace Katas.ExpressionTrees;

/// <summary>
/// Walks an expression tree and produces a human-readable string representation.
/// </summary>
public static class ExpressionDescriber
{
    /// <summary>
    /// Produces a readable string for <paramref name="predicate"/>.
    /// </summary>
    public static string Describe<T>(Expression<Func<T, bool>> predicate) => throw new NotImplementedException();
}
