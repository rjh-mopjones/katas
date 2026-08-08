# Money

> Build the value type every pricing, billing, and ledger system in this codebase would embed — get `equals`/`hashCode`/`compareTo` wrong here and prices silently stop matching.

## The problem
Implement an immutable `Money` value type wrapping a decimal amount and a currency code. Two amounts that a human would call identical — `2.0` and `2.00` — must behave identically everywhere: as equality, as a hash-map key, in a hash-set, and under ordering. Arithmetic only makes sense between amounts in the same currency.

## Requirements
- Creating a value from a decimal amount and a currency code, or from a decimal-formatted string and a currency code; both reject a null amount or currency.
- Adding, subtracting, and multiplying by a scalar factor are supported; adding or subtracting values in different currencies throws `IllegalArgumentException`.
- The amount and currency are both readable back out.
- Equality, hashing, and ordering are all supported, and two amounts that are numerically equal but differently scaled (`2.0` vs `2.00`) must compare and hash as equal.
- Fully immutable: no setters, no mutable state exposed.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/money/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
