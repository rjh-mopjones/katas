# Odds Converter

> Build the conversion core behind any odds-comparison or pricing screen: translate a price between decimal, fractional, and American (moneyline) quotes, and to/from implied probability — exactly, with correct rounding.

## The problem
Bookmakers quote the same price three different ways depending on market and region — decimal (`3.5`), fractional (`5/2`), and American moneyline (`+150` / `-200`) — and every price also implies a probability (`1/decimal`). A pricing or comparison feature has to convert cleanly between all of them without ever losing a fraction of a cent to floating-point drift.

## Requirements
- Converting a fractional quote to decimal: `numerator/denominator + 1` (5/2 → 3.5, 1/1 → 2.0).
- Converting an American (moneyline) quote to decimal: positive → `american/100 + 1` (+150 → 2.5); negative → `100/|american| + 1` (-200 → 1.5). A moneyline of exactly `0` is invalid.
- Converting decimal odds to implied probability: `1/decimalOdds`. The decimal odds must be `> 1`.
- Converting an implied probability back to decimal odds: `1/p`. The probability must be in the open interval `(0, 1)`.
- Converting decimal odds to a fractional quote: `decimalOdds - 1` reduced to a lowest-terms fraction (3.5 → 5/2, 2.0 → 1/1).
- Converting decimal odds to an American (moneyline) quote: `decimalOdds >= 2.0` → round `(decimalOdds - 1) * 100` to a positive line; `decimalOdds < 2.0` → round `-100/(decimalOdds - 1)` to a negative line (2.5 → +150, 1.5 → -200, 2.0 → +100).
- Every conversion rejects out-of-domain input with `IllegalArgumentException`: decimal odds `<= 1`, a probability outside `(0, 1)`, American odds of exactly `0`, non-positive fractional terms. All conversions work over `java.math.BigDecimal` — never floating-point.

## What you're given
- `Fractional` — a validated record for a fractional-odds quote (e.g. `5/2`), always reducible to lowest terms; provided verbatim, you don't need to change it.

You design the entire public API — method names, parameters, return types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/oddsconverter/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
