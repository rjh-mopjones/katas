using System.Linq.Expressions;
using System.Text;

namespace Katas.ExpressionTrees;

/// <summary>
/// Walks an expression tree and produces a human-readable string representation.
/// </summary>
/// <remarks>
/// <para>
/// <b>Use case:</b>  Debugging, logging, and test assertions benefit from a readable description
/// of a predicate.  <c>expression.ToString()</c> works but produces C#-ish output with full type
/// names and <c>AndAlso</c>/<c>OrElse</c> keywords.  <c>Describe</c> produces a cleaner form.
/// </para>
/// <para>
/// <b>Visitor pattern:</b>  The describer extends <see cref="ExpressionVisitor"/> and overrides
/// node-type handlers to build up a string instead of returning a rewritten tree.
/// Because <c>ExpressionVisitor</c> is designed for tree transformation it returns
/// <see cref="Expression"/> nodes; we attach the textual output via a <see cref="StringBuilder"/>
/// side-channel.
/// </para>
/// <para>
/// <b>Scope:</b>  Only the node types most commonly appearing in simple lambda predicates are
/// handled: member access, constants, binary comparisons, and the logical connectives.
/// Unrecognised nodes fall back to <c>node.ToString()</c>.
/// </para>
/// </remarks>
public static class ExpressionDescriber
{
    /// <summary>
    /// Produces a readable string for <paramref name="predicate"/>.
    /// </summary>
    /// <typeparam name="T">The subject type of the predicate.</typeparam>
    /// <param name="predicate">The expression to describe.</param>
    /// <returns>A human-readable description, e.g. <c>(x.Age &gt; 18) AndAlso (x.Name == "Bob")</c>.</returns>
    public static string Describe<T>(Expression<Func<T, bool>> predicate)
    {
        DescribeVisitor visitor = new();
        visitor.Visit(predicate.Body);
        return visitor.Result;
    }

    private sealed class DescribeVisitor : ExpressionVisitor
    {
        private readonly StringBuilder _sb = new();

        public string Result => _sb.ToString();

        protected override Expression VisitBinary(BinaryExpression node)
        {
            bool isLogical = IsLogical(node.NodeType);

            // For logical connectives (AndAlso/OrElse) wrap each operand in parens
            // so the output reads "(left) AndAlso (right)" rather than the ambiguous
            // "left AndAlso right".
            if (isLogical) _sb.Append('(');
            Visit(node.Left);
            if (isLogical) _sb.Append(')');

            _sb.Append($" {Operator(node.NodeType)} ");

            if (isLogical) _sb.Append('(');
            Visit(node.Right);
            if (isLogical) _sb.Append(')');

            return node;
        }

        protected override Expression VisitMember(MemberExpression node)
        {
            // Only print the member name (not the full path) for simple cases.
            if (node.Expression is ParameterExpression param)
            {
                _sb.Append(param.Name).Append('.').Append(node.Member.Name);
            }
            else
            {
                _sb.Append(node.Member.Name);
            }
            return node;
        }

        protected override Expression VisitConstant(ConstantExpression node)
        {
            _sb.Append(node.Value is string s ? $"\"{s}\"" : node.Value?.ToString() ?? "null");
            return node;
        }

        protected override Expression VisitParameter(ParameterExpression node)
        {
            _sb.Append(node.Name);
            return node;
        }

        protected override Expression VisitUnary(UnaryExpression node)
        {
            if (node.NodeType == ExpressionType.Not)
            {
                _sb.Append('!');
                Visit(node.Operand);
                return node;
            }
            return base.VisitUnary(node);
        }

        private static bool IsLogical(ExpressionType type) =>
            type is ExpressionType.AndAlso or ExpressionType.OrElse;

        private static string Operator(ExpressionType type) => type switch
        {
            ExpressionType.Equal              => "==",
            ExpressionType.NotEqual           => "!=",
            ExpressionType.GreaterThan        => ">",
            ExpressionType.GreaterThanOrEqual => ">=",
            ExpressionType.LessThan           => "<",
            ExpressionType.LessThanOrEqual    => "<=",
            ExpressionType.AndAlso            => "AndAlso",
            ExpressionType.OrElse             => "OrElse",
            _                                 => type.ToString()
        };
    }
}
