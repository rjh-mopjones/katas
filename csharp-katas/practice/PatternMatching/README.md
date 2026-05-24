# PatternMatching

Evaluate an expression AST and classify arrays using C# switch expressions, positional patterns, and list patterns.

## The problem

Given a recursive `Expr` AST (data only — provided for you), implement an `Evaluator` that computes the numeric value of any expression. Then implement `ListClassifier` using C# 11 list patterns to classify and summarise integer arrays.

## Requirements

- `Evaluator.Evaluate` must handle all six node types: `Num`, `Add`, `Sub`, `Mul`, `Div`, `Neg`.
- Division by zero must throw `DivideByZeroException`.
- `ListClassifier.Classify` must return strings in the exact format: `"empty"`, `"single: N"`, `"pair: A, B"`, `"many: first=A, last=B, count=N"`.
- `ListClassifier.SumFirstAndLast` must return `0` for empty, single element doubled, and `first + last` for two or more elements.
- Both methods must throw `ArgumentNullException` for a null array.

## What you implement

```csharp
public static class Evaluator
{
    public static double Evaluate(Expr expr);
}

public static class ListClassifier
{
    public static string Classify(int[] items);
    public static int SumFirstAndLast(int[] items);
}
```

The `Expr` hierarchy (`Num`, `Add`, `Sub`, `Mul`, `Div`, `Neg`) is provided verbatim — do not modify it.

## The real challenge

- Use positional patterns (`Add(var l, var r)`) so the switch arms read like mathematical grammar rules.
- Use the C# 11 list pattern `[var first, .., var last]` for the "many" case — note the slice `..` discards the middle without allocating.
- Making the `Evaluator` switch exhaustive (no discard arm needed when all sealed subtypes are covered).

## Run

Write your own tests under `practice.tests/PatternMatching/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~PatternMatching"
```

## Reference

`solution/PatternMatching/` — see `Evaluator.cs` and `ListClassifier.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/operators/patterns
