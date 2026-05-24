# Nullable

Nullable reference types and the `System.Diagnostics.CodeAnalysis` flow-analysis attributes.

## The problem

Implement a set of static helpers that teach the C# compiler stronger nullability contracts through attributes. The goal is to eliminate null-dereference warnings at call sites without sprinkling `!` (null-forgiving) operators everywhere.

## Requirements

- `TryGetValue` must annotate the `out` parameter with `[NotNullWhen(true)]` so callers know the value is non-null inside the `true` branch.
- `FirstNonNullOrDefault` must return a non-null `string` — the fallback guarantees it.
- `LengthOrZero` must return `0` for `null` and `s.Length` otherwise; the compiler should narrow `s` to non-null after the null check.
- `Combine` must return a non-null concatenation of two nullable strings.

## What you implement

```csharp
public static class SafeParsing
{
    public static bool TryGetValue(
        IReadOnlyDictionary<string, string> map,
        string key,
        [NotNullWhen(true)] out string? value);

    public static string FirstNonNullOrDefault(string fallback, params string?[] candidates);
    public static int LengthOrZero(string? s);
    public static string Combine(string? a, string? b);
}
```

## The real challenge

- `out` parameters cannot use expression-body syntax — use a block body for `TryGetValue`.
- Understanding the difference between `[NotNullWhen(true)]` and `[MaybeNullWhen(false)]` (they are duals of each other).
- Keeping the `using System.Diagnostics.CodeAnalysis;` import — it is required for the attribute to resolve.

## Run

Write your own tests under `practice.tests/Nullable/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Nullable"
```

## Reference

`solution/Nullable/` — see `SafeParsing.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/attributes/nullable-analysis
