# Arbitrage

> Given quotes for a market's selections scraped from several bookmakers, find whether the combined best prices offer a risk-free profit — the "surebet" scanner behind every odds-comparison arbitrage tool.

## The problem

A single bookmaker always prices a market with a built-in margin (see `overround`): their own
implied probabilities sum to *more* than 1. But different books disagree on where they load that
margin, so the *best* price for each selection, shopped across several competing books, can
combine into a market whose implied probabilities sum to *less* than 1. When that happens, a
carefully sized bet on every selection — one leg per book — guarantees the same payout no matter
which selection wins, and that payout is strictly more than the total staked.

## Requirements

- `bestOdds(quotes)` — the highest-odds `Quote` per selection across all books; ties broken by book
  name ascending.
- `bookSum(quotes, selections)` — `Σ (1/bestOdds)` over the given selections. If any selection has
  no quote at all, the market is uncoverable — document how you signal that (this kata returns
  `BigDecimal.ONE`, which reads as "no arbitrage").
- `isArbitrage(quotes, selections)` — true iff every selection has a quote **and** `bookSum` is
  strictly less than 1. Exactly `1.0` (break-even) is not an arbitrage.
- `stakes(quotes, selections, totalStake)` — the stake per selection that equalises the payout
  whichever selection wins. Decide what happens when there is no arbitrage (throw, or return
  empty) and document it.
- `guaranteedProfit(quotes, selections, totalStake)` — the riskless profit locked in by staking
  `totalStake` across the arbitrage. Decide the no-arbitrage behaviour and document it.
- Use `BigDecimal` and `compareTo` throughout — never `double` equality — for odds, stakes and
  the `< 1` / `== 1` comparisons.

## What you implement

Implement `ArbitrageDetector` from scratch — the public API is `bestOdds(List<Quote>)`,
`bookSum(List<Quote>, Set<String>)`, `isArbitrage(List<Quote>, Set<String>)`,
`stakes(List<Quote>, Set<String>, BigDecimal)`, and
`guaranteedProfit(List<Quote>, Set<String>, BigDecimal)`. `Quote` (a `book`/`selection`/`odds`
record, with odds validated `> 1` in its compact constructor) is provided as a fixture. You design
the internal comparison, summation, and stake-sizing arithmetic yourself.

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

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/arbitrage/` to drive
your implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference

- Worked solution: `solution/src/main/java/org/kata/arbitrage/`
- Related kata: `overround` (the single-book, `bookSum > 1` mirror image of this problem)
- Java Interview Primer: Q112 (`BigDecimal` vs `double` for money/odds), Q48 (locks for a
  concurrent, streaming-quotes variant of this scanner)

**Extension idea:** make it concurrent — books push quote updates continuously instead of handing
you a fixed `List<Quote>` up front; re-evaluate `bookSum` incrementally as quotes arrive and expire,
and guard the shared best-price table so a scan never reads a half-updated market.
