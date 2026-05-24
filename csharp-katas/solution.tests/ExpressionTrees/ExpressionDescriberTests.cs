using System.Linq.Expressions;

namespace Katas.Tests.ExpressionTrees;

using Katas.ExpressionTrees;

public sealed class ExpressionDescriberTests
{
    private sealed class Item
    {
        public int Age { get; init; }
        public string Name { get; init; } = "";
    }

    // -------------------------------------------------------------------------
    // Simple binary comparisons
    // -------------------------------------------------------------------------

    [Fact]
    public void Describe_Should_ProduceReadableString_ForGreaterThanComparison()
    {
        Expression<Func<Item, bool>> predicate = x => x.Age > 18;
        string description = ExpressionDescriber.Describe(predicate);
        Assert.Equal("x.Age > 18", description);
    }

    [Fact]
    public void Describe_Should_ProduceReadableString_ForEqualityComparison()
    {
        Expression<Func<Item, bool>> predicate = x => x.Name == "Bob";
        string description = ExpressionDescriber.Describe(predicate);
        Assert.Equal("x.Name == \"Bob\"", description);
    }

    [Fact]
    public void Describe_Should_ProduceReadableString_ForLessThanOrEqual()
    {
        Expression<Func<Item, bool>> predicate = x => x.Age <= 65;
        string description = ExpressionDescriber.Describe(predicate);
        Assert.Equal("x.Age <= 65", description);
    }

    // -------------------------------------------------------------------------
    // Compound predicates (AndAlso / OrElse)
    // -------------------------------------------------------------------------

    [Fact]
    public void Describe_Should_ProduceReadableString_ForAndAlso()
    {
        Expression<Func<Item, bool>> a = x => x.Age > 18;
        Expression<Func<Item, bool>> b = x => x.Name == "Bob";
        Expression<Func<Item, bool>> combined = PredicateBuilder.And(a, b);

        string description = ExpressionDescriber.Describe(combined);

        Assert.Equal("(x.Age > 18) AndAlso (x.Name == \"Bob\")", description);
    }

    [Fact]
    public void Describe_Should_ProduceReadableString_ForOrElse()
    {
        Expression<Func<Item, bool>> a = x => x.Name == "Alice";
        Expression<Func<Item, bool>> b = x => x.Name == "Bob";
        Expression<Func<Item, bool>> combined = PredicateBuilder.Or(a, b);

        string description = ExpressionDescriber.Describe(combined);

        Assert.Equal("(x.Name == \"Alice\") OrElse (x.Name == \"Bob\")", description);
    }

    // -------------------------------------------------------------------------
    // Always-true
    // -------------------------------------------------------------------------

    [Fact]
    public void Describe_Should_DescribeAlwaysTruePredicate()
    {
        Expression<Func<Item, bool>> alwaysTrue = PredicateBuilder.True<Item>();
        string description = ExpressionDescriber.Describe(alwaysTrue);
        // True() compiles to "_ => True" — the constant node has value True.
        Assert.Contains("True", description, StringComparison.OrdinalIgnoreCase);
    }
}
