# ExpressionTrees

Build and inspect `Expression<Func<T, bool>>` predicate trees using `ExpressionVisitor`.

## The problem

Implement a `PredicateBuilder` that combines lambda expression trees with `&&` and `||`, and an `ExpressionDescriber` that walks a predicate tree and produces a human-readable string. This is the foundation of how ORM query providers translate LINQ to SQL.

## Requirements

**PredicateBuilder**
- `True<T>()` returns `_ => true`, an AND identity.
- `And<T>(a, b)` returns a new expression equivalent to `x => a(x) && b(x)`.
- `Or<T>(a, b)` returns a new expression equivalent to `x => a(x) || b(x)`.
- Combined expressions must share a single `ParameterExpression` (rebind `b`'s parameter to `a`'s).

**ExpressionDescriber**
- `Describe<T>(predicate)` returns a readable string such as `(x.Age > 18) AndAlso (x.Name == "Bob")`.
- Must handle: member access, constants, binary comparisons, `AndAlso`, `OrElse`, and `Not`.

## What you implement

```csharp
public static class PredicateBuilder
{
    public static Expression<Func<T, bool>> True<T>();
    public static Expression<Func<T, bool>> And<T>(
        Expression<Func<T, bool>> a,
        Expression<Func<T, bool>> b);
    public static Expression<Func<T, bool>> Or<T>(
        Expression<Func<T, bool>> a,
        Expression<Func<T, bool>> b);
}

public static class ExpressionDescriber
{
    public static string Describe<T>(Expression<Func<T, bool>> predicate);
}
```

## The real challenge

- Two independently-created lambda expressions have *different* `ParameterExpression` objects even when named identically. You must write a visitor that replaces one with the other before combining bodies.
- `ExpressionVisitor` is designed for tree transformation — producing a string requires a side-channel (e.g. `StringBuilder`) because the `Visit*` methods must return `Expression`.
- Expression trees are immutable; the visitor returns a new tree, leaving the originals unchanged.

## Run

Write your own tests under `practice.tests/ExpressionTrees/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~ExpressionTrees"
```

## Reference

`solution/ExpressionTrees/` — see `PredicateBuilder.cs` and `ExpressionDescriber.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/programming-guide/concepts/expression-trees/
