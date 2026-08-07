# Arbitrage

## Approach

`bestOdds` folds `List<Quote>` into a `Map<String selection, Quote>` in a single pass, keeping a
quote only when it beats the current best (higher odds, or equal odds with a lexicographically
smaller book name). A `LinkedHashMap` is enough — there's no need for anything fancier since every
downstream method needs the full best-price map anyway, not just the top-of-book entry.

`bookSum` reuses `bestOdds`, checks that every requested selection is covered
(`keySet().containsAll(selections)`), and if not returns `BigDecimal.ONE` — a sentinel that reads
as "no arbitrage" to `isArbitrage` without a special-cased null/Optional return. Otherwise it sums
`1/odds` for each selection using a fixed `MathContext(12, HALF_UP)` so the repeating decimals from
odds like `3` (`1/3 = 0.333…`) don't force a `BigDecimal` division `ArithmeticException` and don't
drift across repeated calls.

`isArbitrage` is a thin wrapper: coverage check, then `bookSum(...).compareTo(BigDecimal.ONE) < 0`
— strict, so break-even is deliberately excluded.

`stakes` derives each selection's stake algebraically from "payout must be identical whichever
selection wins": if `stakeI = P / oddsI` for a constant payout `P`, then summing over all
selections and solving for `P` against a fixed bankroll gives
`stakeI = totalStake * (1/oddsI) / bookSum`. Each stake is rounded independently to 2 decimal
places (`HALF_UP`) since real venues only accept whole-cent stakes. It throws
`IllegalStateException` when `isArbitrage` is false — there is no valid split to return, so a
throw is more honest than an empty map a caller might silently iterate over as "no stakes needed."

`guaranteedProfit` computes `totalStake * (1/bookSum) - totalStake` directly from the unrounded
`bookSum` — the *theoretical* edge — rather than summing the rounded stakes' payouts. It returns
`BigDecimal.ZERO` (not a throw) when there's no arbitrage, because "zero profit" is itself a
meaningful answer here, unlike `stakes`, which has no meaningful empty/zero value to fall back to.

No concurrency: the whole computation is a pure, single-pass function over an immutable
`List<Quote>` snapshot — there's no shared mutable state to guard.

## The real challenge

- **Best price per selection** — the market you actually trade isn't any single book's list of
  quotes, it's the *best* available price per selection across every book. Get `bestOdds` right
  (including a deterministic tie-break) before anything downstream can be trusted.
- **The Σ(1/odds) < 1 condition** — implied probability is `1/odds`; the arbitrage condition is
  that the best-price book, summed over every selection in the market, comes in under 100%. Compute
  it with `BigDecimal` and compare with `compareTo`, since `1/3` and friends have no exact binary
  (or decimal) representation and `==` on `double` is exactly the bug this kata is designed to
  surface.
- **Equal-payout stake sizing** — solve `stakeᵢ = totalStake · (1/oddsᵢ) / bookSum` so that
  `stakeᵢ · oddsᵢ` is the same constant `P` for every selection. Derive that formula yourself from
  "payout must be equal whichever selection wins" rather than memorising it.
- **The missing-selection and break-even edges** — a selection nobody quotes cannot be hedged, so
  it must read as "no arbitrage", not as a `NullPointerException` or a partial book. Break-even
  (`bookSum == 1`) must also read as "no arbitrage" — a real trading system that treats `<= 1` as
  profitable will happily stake money for a guaranteed loss the moment rounding tips it the wrong
  way.
- **Stake rounding erodes the edge** — real venues only accept whole-currency-unit stakes, so
  `stakes()` has to round. Rounding every selection independently means the rounded stakes can sum
  to a few cents more or less than `totalStake`, and — because payout is `stake · odds` — rounding
  can shave a sliver off the theoretical edge (or, on a wafer-thin arb, wipe it out entirely).
  Decide whether `guaranteedProfit` reports the theoretical unrounded edge or re-derives it from
  the rounded stakes, and document the choice; a production system usually rounds all-but-one stake
  and assigns the remainder to the last leg to preserve the edge exactly.

## Common mistakes & senior signal

- **Using `double` for odds/stakes** — `==` and `<` comparisons on `double` are exactly wrong for
  a strict `< 1` financial threshold; reaching for `BigDecimal` + `compareTo` unprompted, and
  picking a deliberate `MathContext`/scale for the repeating-decimal divisions, is the strongest
  senior signal in this kata.
- **Treating `bookSum == 1` as an arbitrage** — an off-by-one on the inequality direction turns a
  guaranteed-wash edge case into a fake profitable trade; a strong answer calls this out explicitly
  and tests it.
- **Letting a missing selection NPE or silently skip** — iterating only over selections that
  happen to have quotes (instead of validating full coverage first) produces a partial, unhedgeable
  "arbitrage." A senior answer treats coverage as step zero, before any arithmetic.
- **Rounding stakes and then re-summing for profit without noticing the drift** — conflating
  "theoretical edge" with "edge after rounding" is a subtle bug that only shows up on thin arbs;
  documenting which one you're returning (and why) is the kind of decision an interviewer wants to
  hear reasoned out loud.
- **Ignoring the tie-break** — an unstable `bestOdds` (e.g. "first quote wins" dependent on list
  order) makes the whole scanner non-reproducible; a deterministic ascending-book-name tie-break is
  a small detail that separates a careful implementation from a merely-passing one.

## Extensions

- **Streaming quotes** — books update prices continuously; re-evaluate `bookSum` incrementally as
  quotes arrive/expire instead of rescanning the full list, and guard the shared best-price table
  with a lock (or shard per market) for concurrent updates.
- **Transaction costs** — commission or a maximum stake per book erodes the edge further; subtract
  an effective-odds haircut before summing.
- **Stale-price risk** — a quote can move or be pulled between detection and execution ("bet
  slippage"); a real system would re-validate prices immediately before placing each leg and abort
  the whole split if any leg fails.
- **Multi-currency books** — convert to a common currency (with its own rounding/erosion
  trade-off) before summing implied probabilities.

## Reference

- Worked solution: this package (`solution/src/main/java/org/kata/arbitrage/`)
- Java Interview Primer: Q112 (`BigDecimal` vs `double` for money/odds), Q48 (locks for a
  concurrent, streaming-quotes variant of this scanner)
