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

## The real challenge
- **`BigDecimal` scale is part of its state**: `new BigDecimal("2.0")` and `new BigDecimal("2.00")` are *not* `.equals()` — they have different scale, even though `compareTo` says they're the same value. Store the amount however you like, but decide **when** you normalise it.
- **Normalise once, at construction**: rescale to a fixed number of decimal places (with a defined `RoundingMode`) in the constructor, so every stored `Money` is already canonical. Do this and `equals`/`hashCode`/`compareTo` fall out consistent for free; skip it and `2.0`/`2.00` diverge depending on which method you ask.
- **`equals` must never throw**: a `HashMap`/`HashSet` will call `equals` on arbitrary pairs internally (e.g. during resize) — cross-currency amounts must just be *unequal*, not an exception.
- **`compareTo` is allowed to throw**: unlike `equals`, there's no sane way to order `5 USD` against `5 GBP` without a conversion rate, so `compareTo` should reject a currency mismatch loudly (`IllegalArgumentException`) rather than guess. That makes it a partial order — total only within one currency.
- **Compare amounts with `compareTo`, never `BigDecimal.equals`** — even after your normalisation, reach for `compareTo`/`signum` for any value comparison inside your own methods; it's the habit that survives if the normalisation invariant is ever relaxed.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/money/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/money/`
- Java Interview Primer: Effective Java Items 10-14 (equals/hashCode/compareTo contracts)
