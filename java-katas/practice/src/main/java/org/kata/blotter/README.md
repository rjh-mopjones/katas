# Blotter

> Answer P&L reporting queries over a desk's trading blotter using `Collectors` — the everyday tool behind almost every end-of-day report.

## The problem
A trading blotter is a flat list of executed `Trade` records. Desk heads and risk want the same list sliced four different ways: pnl summed per desk and per symbol, winners split from losers, the best and worst trade in one look, and how often each free-form tag (`"algo"`, `"hedge"`, `"manual"`) shows up. Every query reads the same input list and returns a fresh result — nothing is mutated, nothing is stateful.

## Requirements
- Summing pnl per desk, then per symbol within that desk, must be available as a nested breakdown.
- Splitting trades into winners and losers: one group holds trades with `pnl > 0`, the other holds the rest (losers and flat trades).
- Finding the best and worst pnl across all trades in a single pass; on an empty input, both come back absent.
- Counting how many times each free-form tag occurs across all trades; a trade with no tags contributes nothing.
- Every query must handle an empty list of trades without throwing.

## What you're given
`Side` (a `BUY`/`SELL` enum) and `Trade` (a `desk`/`symbol`/`side`/`pnl`/`tags` record) are
provided as fixtures. You design the entire public API — method names, parameters, return
types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/blotter/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
