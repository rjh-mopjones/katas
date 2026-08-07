# Overround

> Given a bookmaker's decimal odds for a market, measure the built-in margin and strip it out to recover fair, arbitrage-free probabilities — the calculation behind every "implied probability" column on a sportsbook or exchange.

## The problem

A decimal-odds market implies a probability per outcome: `p = 1/odds`. A perfectly fair book has
those implied probabilities sum to exactly `1.0`. In practice a bookmaker shortens every price
slightly so the book sums to *more* than `1.0` — the excess ("overround", "vig", "juice") is the
house edge baked into the prices. To compare a book's true view of the outcome (for pricing,
trading, or spotting value) you need to measure that margin and remove it, recovering
probabilities that sum back to `1.0`.

## Requirements

- `bookSum(decimalOdds)` — `Σ (1/oddsᵢ)`, the raw book total.
- `overround(decimalOdds)` — `bookSum − 1`, the margin (e.g. `0.05` = 5%).
- `fairProbabilities(decimalOdds, method)` — de-vigged probabilities summing to `1` (within a small
  epsilon), computed by one of three `Method`s: `PROPORTIONAL`, `ADDITIVE`, `POWER`.
- `fairOdds(decimalOdds, method)` — `1/pᵢ` from the fair probabilities.
- Any odds `<= 1`, or an empty list, throws `IllegalArgumentException`.
- Works for 2-way (moneyline) and N-way (e.g. race, match-result) markets alike.

## What you implement

Implement `Overround` from scratch — the public API is `bookSum`, `overround`,
`fairProbabilities(List<BigDecimal>, Method)`, and `fairOdds(List<BigDecimal>, Method)`. The
`Method` enum (`PROPORTIONAL`, `ADDITIVE`, `POWER`) is provided as a fixture. You design the
internal arithmetic, rounding, and the `POWER` root-find yourself.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/overround/` to drive
your implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
