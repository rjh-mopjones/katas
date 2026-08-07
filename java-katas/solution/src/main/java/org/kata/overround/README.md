# Overround

## Approach
Every method starts from the same implied-probability vector, `pᵢ = 1/oddsᵢ`, computed in
`BigDecimal` at a fixed working scale (10 places, `HALF_UP`) rather than `double` — odds arithmetic
compounds rounding error across a multi-leg book if it's done in binary floating point.
`bookSum`/`overround` just sum and offset that vector.

The three `fairProbabilities` methods differ only in how they redistribute the margin, then all three
funnel through the same `normalize` step (divide every value by the group's own total) before
returning:
- **PROPORTIONAL** scales every probability down by the same factor (`pᵢ / bookSum`) — a straight
  division, always well-behaved.
- **ADDITIVE** subtracts a flat share of the margin from every outcome, clamps any resulting negative
  at zero, then relies on the shared `normalize` step to restore the sum-to-1 contract.
- **POWER** finds an exponent `k` with `Σ pᵢ^k = 1` and sets `pᵢ' = pᵢ^k`. There's no closed form, so
  it's solved with plain bisection in `double` (cheap iteration, converted back to `BigDecimal` only
  for the final result): since each `pᵢ ∈ (0, 1)`, `pᵢ^k` is strictly decreasing in `k`, so doubling an
  upper bound until the residual goes non-positive always brackets the single root, and a fixed
  iteration cap plus tolerance keeps the loop from spinning.

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

## Common mistakes & senior signal
- Doing the bulk of the arithmetic in `double` — small errors on a multi-leg book compound into
  visibly wrong probabilities.
- Skipping renormalisation after the `ADDITIVE` clamp — the output silently no longer sums to 1.
- Not clamping `ADDITIVE`'s negative results at all, letting a longshot report a negative probability.
- Assuming a closed-form solution exists for the `POWER` exponent instead of recognising it needs a
  numeric root-find.
- An unbounded bisection loop with no iteration cap or tolerance — fine on well-behaved input, a
  latent hang on an edge case.
- Missing the `odds <= 1` / empty-list validation, so a degenerate market silently produces garbage
  (or a divide-by-zero) instead of failing fast.

## Extensions
- **Shin's method** — a fourth de-vig model that solves for an implied insider-trading fraction
  rather than a flat exponent; strictly more accurate than `POWER` for markets with informed money,
  at the cost of a 2D solve.
- **American / fractional odds** — accept an odds format enum and convert to decimal at the boundary
  instead of requiring the caller to pre-convert.
- **Arbitrage detection** — expose a helper that flags `overround < 0` (a mispriced, arbable book)
  distinctly from the normal positive-margin case.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/overround/`)
- Java Interview Primer: Q112 (BigDecimal vs double for money/odds), Q140 (numerical root-finding —
  bisection vs Newton)
