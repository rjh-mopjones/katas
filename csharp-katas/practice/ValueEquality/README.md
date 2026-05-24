# ValueEquality

Implement a `Money` value type as a `readonly struct` with full value-equality, ordering, and arithmetic.

## The problem

Represent a monetary amount and currency as an immutable value type where two instances are equal when their amount and (case-insensitive) currency match. The type must be safely usable in dictionaries, sorted collections, and arithmetic expressions.

## Requirements

- Constructor normalises `Currency` to upper-case and rejects blank currencies.
- `Equals`/`GetHashCode`/`==`/`!=` all agree: same amount + same currency (case-insensitive) → equal.
- `+` and `-` operators throw `InvalidOperationException` for mismatched currencies.
- `*` operator scales by a `decimal` scalar; currency is preserved.
- `CompareTo` and relational operators (`<`, `>`, `<=`, `>=`) order within the same currency and throw for mismatches.
- `Zero(currency)` factory returns an amount of `0` in the given currency.
- `ToString()` returns e.g. `"10.50 USD"`.

## What you implement

```csharp
public readonly struct Money : IEquatable<Money>, IComparable<Money>
{
    public decimal Amount { get; }
    public string Currency { get; }
    public Money(decimal amount, string currency);
    public static Money Zero(string currency);
    public static Money operator +(Money left, Money right);
    public static Money operator -(Money left, Money right);
    public static Money operator *(Money money, decimal scalar);
    public static Money operator *(decimal scalar, Money money);
    public static bool operator ==(Money left, Money right);
    public static bool operator !=(Money left, Money right);
    public static bool operator <(Money left, Money right);
    public static bool operator >(Money left, Money right);
    public static bool operator <=(Money left, Money right);
    public static bool operator >=(Money left, Money right);
    public int CompareTo(Money other);
    public bool Equals(Money other);
    public override bool Equals(object? obj);
    public override int GetHashCode();
    public override string ToString();
}
```

## The real challenge

- A `readonly struct` cannot have mutable fields, so you must store `Currency` already normalised.
- `GetHashCode` must be consistent with `Equals` — if two instances are equal, their hash codes must match.
- The constructor body must use block-body syntax (`{ ... }`) because expression bodies (`=>`) cannot throw and assign fields simultaneously in a struct.

## Run

Write your own tests under `practice.tests/ValueEquality/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~ValueEquality"
```

## Reference

`solution/ValueEquality/` — see `Money.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/builtin-types/struct
