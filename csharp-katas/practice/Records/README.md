# Records

Immutable data modelling with C# `record` types, `with`-expressions, and structural equality.

## The problem

Model a `Customer` as an immutable value object that holds a name, a postal `Address`, and a list of classification tags. Provide pure operations that produce updated copies without mutating the originals.

## Requirements

- `Customer` must validate that `Name` is not blank at construction time (compact constructor).
- `Customer.TagCount` must return the number of tags without participating in `Equals`/`GetHashCode`.
- Equality on `Customer` must compare `Tags` by sequence (not by reference).
- `CustomerOperations.MoveCity` must return a new `Customer` with a nested `Address` copy.
- `CustomerOperations.AddTag` must return a new `Customer` with the tag appended; original is untouched.

## What you implement

```csharp
// Customer.cs
public record Customer(string Name, Address Address, IReadOnlyList<string> Tags)
{
    public string Name { get; init; }   // validates non-blank
    public int TagCount { get; }        // derived; excluded from equality
    public virtual bool Equals(Customer? other);
    public override int GetHashCode();
}

// CustomerOperations.cs
public static class CustomerOperations
{
    public static Customer MoveCity(Customer customer, string newCity);
    public static Customer AddTag(Customer customer, string tag);
}
```

`Address` is provided verbatim — do not modify it.

## The real challenge

- Understanding how the compact constructor runs *before* property assignments and why that matters for validation.
- Overriding `Equals`/`GetHashCode` on a record to achieve deep collection equality while preserving the synthesised copy constructor.
- Composing nested `with`-expressions without mutating either the outer or inner record.

## Run

Write your own tests under `practice.tests/Records/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Records"
```

## Reference

`solution/Records/` — see `Customer.cs` and `CustomerOperations.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/builtin-types/record
