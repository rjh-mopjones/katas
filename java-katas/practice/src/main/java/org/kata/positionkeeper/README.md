# Position & Exposure Keeper

> Build the risk component behind a betting exchange's bet-acceptance gate — a thread-safe running book of every matched bet, per market, that answers "what do we lose?" in real time.

## The problem
A betting exchange matches thousands of bets a second across many concurrent markets. Before accepting the next bet, risk needs to know, right now, what the book stands to lose under every possible result. Implement `PositionKeeper`: a store that accumulates matched bets and answers profit/loss and worst-case-liability queries without ever replaying the full bet history.

## Requirements
- `apply(Bet bet)` accumulates a matched bet into its market's book; safe for many concurrent callers across many markets.
- `pnlIfWins(String market, String selection)` returns the whole market's profit/loss if `selection` is the winning outcome, summed over every bet placed in that market. Decimal odds `O`, stake `S`: a `BACK` bet pays `+S*(O-1)` if its selection wins, `-S` otherwise; a `LAY` bet pays `-S*(O-1)` if its selection wins, `+S` otherwise.
- `worstCaseLiability(String market)` returns the largest possible loss as a **non-negative** `BigDecimal` — the worst `pnlIfWins` across every selection that has a bet in the market, *plus* the "other outcome" case where none of those selections wins. Return `0` for an unknown or empty market, and `0` (never negative) for a market that cannot lose money.
- `totalMatchedStake(String selection)` returns the sum of stakes applied to that selection, across all markets.
- All of the above must behave correctly under concurrent `apply` calls on the same market and the same selection — no lost updates, no torn reads.

## What you implement
Implement `PositionKeeper` from scratch — the public API is `apply(Bet)`, `pnlIfWins(String, String)`, `worstCaseLiability(String)`, and `totalMatchedStake(String)`. You design the internal per-market bookkeeping, the payoff/liability math, and the concurrency strategy yourself.

(`Bet` record and `Side` enum are provided as fully working scaffolding.)

## The real challenge
- **The payoff math collapses to running totals.** Don't replay bets on every read. Per market, track `backStakeTotal`, `layStakeTotal`, and per-selection `backOddsStake[sel] = Σ stake*odds` / `layOddsStake[sel] = Σ stake*odds` for back/lay bets on `sel`. Then `pnlIfWins(w) = (backOddsStake[w] - backStakeTotal) + (layStakeTotal - layOddsStake[w])` — O(1) per read regardless of bet history length.
- **Worst case includes the "other outcome".** The candidate outcomes for `worstCaseLiability` are every selection actually bet on, plus the case where *none* of them wins (`pnl = layStakeTotal - backStakeTotal`). A pure-back book's worst case is usually that "other outcome" case, not any of the backed selections — a classic exchange risk trap if you only scan bet selections.
- **Per-market locking is the right grain.** A live exchange runs many markets concurrently but each individual market sees comparatively few concurrent bets; guard each market's running totals with its own lock (not one global lock, not lock-free per-field, which risks torn reads across the four numbers).
- **BigDecimal throughout.** Stakes, odds, and every derived total use `BigDecimal` with `compareTo` — never `==` or `double` — money arithmetic with binary floats silently corrupts real liability figures.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/positionkeeper/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/positionkeeper/`
- Java Interview Primer: Q48 (locks / `ConcurrentHashMap`), Q12 (`BigDecimal` for money)
