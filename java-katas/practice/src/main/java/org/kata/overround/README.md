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

## The real challenge

- **PROPORTIONAL** — scale every implied probability down by the same factor, `bookSum`:
  `pᵢ = (1/oᵢ) / bookSum`. Simple and always well-behaved (never negative), but it assumes the
  bookmaker loads margin onto every outcome in proportion to its own probability — not how real
  favourite-longshot pricing actually works.
- **ADDITIVE** — subtract an equal *absolute* share of the margin from every outcome:
  `pᵢ = 1/oᵢ − (bookSum − 1)/n`. The trade-off: for an extreme longshot this can drive the
  probability negative, since a flat subtraction can exceed a tiny implied probability. You have to
  decide what to do about it (clamping at zero and renormalising the remainder is the usual fix) —
  and document the choice, because it changes the numbers.
- **POWER** — find a single exponent `k` such that `Σ (1/oᵢ)^k = 1`, then `pᵢ = (1/oᵢ)^k`. This
  matches the empirical "favourite-longshot bias" (bookmakers load disproportionately more margin
  onto longshots) better than the other two. There is no closed form for `k`; you solve it
  numerically — bisection is enough. The values are all in `(0, 1)`, which makes `pᵢ^k` monotonic in
  `k`, so a bracket-and-bisect converges reliably; pick a sane tolerance and iteration cap so it
  can't spin forever.
- **BigDecimal precision** — odds and probability arithmetic should not be done in raw `double`
  (`1/3` and friends have no exact binary representation, and rounding error compounds across a
  multi-leg book). Do the bulk of the arithmetic in `BigDecimal` with a fixed working scale and
  rounding mode; it's fine for the `POWER` root-find itself to work in `double` internally (cheap
  iteration) as long as you convert the final answer back.
- **Why renormalise** — whichever method you use, the *raw* output rarely sums to exactly `1`
  (rounding, or the `ADDITIVE` clamp). Dividing every value by the group's own total is what
  restores the "probabilities sum to 1" contract — decide where in your pipeline that
  renormalisation step belongs.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/overround/` to drive
your implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference

- Worked solution: `solution/src/main/java/org/kata/overround/`
- Java Interview Primer: Q112 (BigDecimal vs double for money/odds), Q140 (numerical root-finding —
  bisection vs Newton)
