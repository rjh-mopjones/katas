# Money

> Build the value type every pricing, billing, and ledger system in this codebase would embed — get `equals`/`hashCode`/`compareTo` wrong here and prices silently stop matching.

## The problem
Implement an immutable `Money` value type wrapping a `BigDecimal` amount and a currency code. Two amounts that a human would call identical — `2.0` and `2.00` — must behave identically everywhere: as `equals`, as a `HashMap` key, in a `HashSet`, and under `compareTo`. Arithmetic only makes sense between amounts in the same currency.

## Requirements
- `Money(BigDecimal amount, String currency)` and `static Money of(String amount, String currency)`; reject a null amount or currency.
- `Money plus(Money other)`, `Money minus(Money other)`, `Money times(BigDecimal factor)` — `plus`/`minus` require the same currency and throw `IllegalArgumentException` otherwise.
- `BigDecimal amount()`, `String currency()`.
- `boolean equals(Object)`, `int hashCode()`, and `implements Comparable<Money>` with `int compareTo(Money)`.
- Fully immutable: no setters, no mutable state exposed.

## What you implement
Implement `Money` from scratch — the public API is the two constructors, `plus`/`minus`/`times`, `amount()`/`currency()`, and the `equals`/`hashCode`/`compareTo` trio. You decide how the amount is stored and normalised internally.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/money/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
