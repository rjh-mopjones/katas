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

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/arbitrage/` to drive
your implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
