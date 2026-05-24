using System.Linq.Expressions;

namespace Katas.Tests.ExpressionTrees;

using Katas.ExpressionTrees;

public sealed class PredicateBuilderTests
{
    /// <summary>A simple POCO used as the subject type for predicate tests.</summary>
    private sealed class Person
    {
        public int Age { get; init; }
        public string Name { get; init; } = "";
    }

    // -------------------------------------------------------------------------
    // True<T> — identity element
    // -------------------------------------------------------------------------

    [Fact]
    public void True_Should_AlwaysReturnTrue_ForAnyInput()
    {
        Func<Person, bool> alwaysTrue = PredicateBuilder.True<Person>().Compile();

        Assert.True(alwaysTrue(new Person { Age = 0, Name = "" }));
        Assert.True(alwaysTrue(new Person { Age = 100, Name = "Alice" }));
    }

    // -------------------------------------------------------------------------
    // And<T>
    // -------------------------------------------------------------------------

    [Fact]
    public void And_Should_ReturnTrue_OnlyWhenBothPredicatesMatch()
    {
        Expression<Func<Person, bool>> isAdult = p => p.Age >= 18;
        Expression<Func<Person, bool>> isNamed = p => p.Name == "Alice";

        Func<Person, bool> combined = PredicateBuilder.And(isAdult, isNamed).Compile();

        Assert.True(combined(new Person { Age = 25, Name = "Alice" }));
        Assert.False(combined(new Person { Age = 25, Name = "Bob" }));
        Assert.False(combined(new Person { Age = 16, Name = "Alice" }));
        Assert.False(combined(new Person { Age = 16, Name = "Bob" }));
    }

    [Fact]
    public void And_Should_FilterCollection_Correctly()
    {
        Expression<Func<Person, bool>> isAdult = p => p.Age >= 18;
        Expression<Func<Person, bool>> longName = p => p.Name.Length > 3;

        Func<Person, bool> both = PredicateBuilder.And(isAdult, longName).Compile();

        var people = new[]
        {
            new Person { Age = 20, Name = "Alice" }, // pass
            new Person { Age = 20, Name = "Bob" },   // fail (short name)
            new Person { Age = 15, Name = "Alice" }, // fail (not adult)
            new Person { Age = 15, Name = "Bob" },   // fail (both)
        };

        Person[] matches = people.Where(both).ToArray();

        Assert.Single(matches);
        Assert.Equal("Alice", matches[0].Name);
    }

    // -------------------------------------------------------------------------
    // Or<T>
    // -------------------------------------------------------------------------

    [Fact]
    public void Or_Should_ReturnTrue_WhenEitherPredicateMatches()
    {
        Expression<Func<Person, bool>> isAlice = p => p.Name == "Alice";
        Expression<Func<Person, bool>> isBob = p => p.Name == "Bob";

        Func<Person, bool> combined = PredicateBuilder.Or(isAlice, isBob).Compile();

        Assert.True(combined(new Person { Name = "Alice" }));
        Assert.True(combined(new Person { Name = "Bob" }));
        Assert.False(combined(new Person { Name = "Carol" }));
    }

    // -------------------------------------------------------------------------
    // Chaining And/Or
    // -------------------------------------------------------------------------

    [Fact]
    public void And_Or_Chain_Should_ProduceCorrectPredicate()
    {
        // (age > 18 AND name == "Alice") OR (age > 18 AND name == "Bob")
        Expression<Func<Person, bool>> isAdult = p => p.Age > 18;
        Expression<Func<Person, bool>> isAlice = p => p.Name == "Alice";
        Expression<Func<Person, bool>> isBob = p => p.Name == "Bob";

        var adultAlice = PredicateBuilder.And(isAdult, isAlice);
        var adultBob = PredicateBuilder.And(isAdult, isBob);
        Func<Person, bool> combined = PredicateBuilder.Or(adultAlice, adultBob).Compile();

        Assert.True(combined(new Person { Age = 25, Name = "Alice" }));
        Assert.True(combined(new Person { Age = 30, Name = "Bob" }));
        Assert.False(combined(new Person { Age = 25, Name = "Carol" }));
        Assert.False(combined(new Person { Age = 15, Name = "Alice" }));
    }

    // -------------------------------------------------------------------------
    // True used as fold identity
    // -------------------------------------------------------------------------

    [Fact]
    public void True_Should_ServeAsIdentity_WhenFoldingPredicates()
    {
        var predicates = new List<Expression<Func<Person, bool>>>
        {
            p => p.Age > 10,
            p => p.Age < 50,
            p => p.Name.Length > 0,
        };

        Expression<Func<Person, bool>> combined =
            predicates.Aggregate(PredicateBuilder.True<Person>(), PredicateBuilder.And);

        Func<Person, bool> fn = combined.Compile();

        Assert.True(fn(new Person { Age = 25, Name = "Alice" }));
        Assert.False(fn(new Person { Age = 5, Name = "Alice" }));
        Assert.False(fn(new Person { Age = 25, Name = "" }));
    }
}
