# Blotter

> Answer P&L reporting queries over a desk's trading blotter using `Collectors` — the everyday tool behind almost every end-of-day report.

## The problem
A trading blotter is a flat list of executed `Trade` records. Desk heads and risk want the same list sliced four different ways: pnl summed per desk and per symbol, winners split from losers, the best and worst trade in one look, and how often each free-form tag (`"algo"`, `"hedge"`, `"manual"`) shows up. Every query reads the same input list and returns a fresh result — nothing is mutated, nothing is stateful.

## Requirements
- `pnlByDeskAndSymbol(List<Trade>)` returns `Map<String, Map<String, BigDecimal>>` — pnl summed per desk, then per symbol within that desk.
- `winnersVsLosers(List<Trade>)` returns `Map<Boolean, List<Trade>>` — `true` holds trades with `pnl > 0`, `false` holds the rest (losers and flat trades).
- `minMaxPnl(List<Trade>)` returns a `MinMax` record (`BigDecimal min`, `BigDecimal max`) found in **one pass** over the trades; on an empty list both fields are `null`.
- `tagCounts(List<Trade>)` returns `Map<String, Long>` — how many times each tag occurs across all trades (a trade with no tags contributes nothing).
- Every method must handle an empty `List<Trade>` input without throwing.

## What you implement
Implement `Blotter` from scratch: the four query methods above, all taking a `List<Trade>` and returning a fresh `Map` or `MinMax`. `Side` and `Trade` are provided.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/blotter/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
