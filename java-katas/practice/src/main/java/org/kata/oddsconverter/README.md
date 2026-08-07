# Odds Converter

> Build the conversion core behind any odds-comparison or pricing screen: translate a price between decimal, fractional, and American (moneyline) quotes, and to/from implied probability — exactly, with correct rounding.

## The problem
Bookmakers quote the same price three different ways depending on market and region — decimal (`3.5`), fractional (`5/2`), and American moneyline (`+150` / `-200`) — and every price also implies a probability (`1/decimal`). A pricing or comparison feature has to convert cleanly between all of them without ever losing a fraction of a cent to floating-point drift.

## Requirements
- `decimalFromFractional(Fractional f)`: `numerator/denominator + 1` (5/2 → 3.5, 1/1 → 2.0).
- `decimalFromAmerican(int american)`: positive → `american/100 + 1` (+150 → 2.5); negative → `100/|american| + 1` (-200 → 1.5). `american == 0` is invalid.
- `impliedProbability(BigDecimal decimalOdds)`: `1/decimalOdds`. `decimalOdds` must be `> 1`.
- `decimalFromProbability(BigDecimal p)`: `1/p`. `p` must be in the open interval `(0, 1)`.
- `fractionalFromDecimal(BigDecimal decimalOdds)`: `decimalOdds - 1` reduced to a lowest-terms fraction (3.5 → 5/2, 2.0 → 1/1).
- `americanFromDecimal(BigDecimal decimalOdds)`: `decimalOdds >= 2.0` → round `(decimalOdds - 1) * 100` to a positive line; `decimalOdds < 2.0` → round `-100/(decimalOdds - 1)` to a negative line (2.5 → +150, 1.5 → -200, 2.0 → +100).
- Every method rejects out-of-domain input with `IllegalArgumentException`: decimal odds `<= 1`, a probability outside `(0, 1)`, American odds of exactly `0`, non-positive fractional terms.

## What you implement
Implement `OddsConverter` from scratch — the public API is the six conversion methods above, all working over `java.math.BigDecimal`. `Fractional` is provided as a fixture (a validated `record`); you don't need to change it. You choose the rounding scale/mode and the fractional-reduction algorithm.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/oddsconverter/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
