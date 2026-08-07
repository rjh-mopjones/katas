# Position & Exposure Keeper

## Approach
Rather than replaying bet history on every read, each market keeps four running numbers:
`backStakeTotal`, `layStakeTotal`, and per-selection `backOddsStake[sel] = Σ stake*odds` /
`layOddsStake[sel] = Σ stake*odds`. `apply` folds a new bet into exactly the relevant totals in O(1).
`pnlIfWins(w)` then collapses to `(backOddsStake[w] - backStakeTotal) + (layStakeTotal -
layOddsStake[w])`: the first term says "backers of `w` collect their odds-stake, everyone else's back
stake is forfeit"; the second is the lay mirror image. No per-bet iteration, regardless of how many
bets have been applied.

`worstCaseLiability` scans a small candidate set — every selection that has at least one bet on it,
plus the "other outcome" case where none of them wins (`pnl = layStakeTotal - backStakeTotal`) — takes
the minimum `pnlIfWins` across that set, and reports `max(0, -minPnl)` so an unlosable market reports
`0` rather than a negative "liability". The candidate set doesn't need to include every possible
outcome (e.g. "top goalscorer" has an effectively unbounded outcome space) because any selection
nobody bet on has exactly the "other outcome" pnl, which is already a candidate.

Concurrency is scoped per market: a `ConcurrentHashMap<String, Market>` and each `Market` is a small
mutable aggregate guarded by its own `ReentrantLock`, held across both the write in `apply` and the
reads, so a reader can never observe a torn update (one total updated, its paired bucket not yet). One
lock per market is the right grain — a live exchange runs far more markets than any single market has
concurrent bets. `totalMatchedStake` lives in a separate `ConcurrentHashMap<String, BigDecimal>`
updated via `merge`, independent of any market lock, because it only needs per-key atomicity, not
consistency with a market's book.

## The real challenge
- **The payoff math collapses to running totals.** Don't replay bets on every read. Per market, track `backStakeTotal`, `layStakeTotal`, and per-selection `backOddsStake[sel] = Σ stake*odds` / `layOddsStake[sel] = Σ stake*odds` for back/lay bets on `sel`. Then `pnlIfWins(w) = (backOddsStake[w] - backStakeTotal) + (layStakeTotal - layOddsStake[w])` — O(1) per read regardless of bet history length.
- **Worst case includes the "other outcome".** The candidate outcomes for `worstCaseLiability` are every selection actually bet on, plus the case where *none* of them wins (`pnl = layStakeTotal - backStakeTotal`). A pure-back book's worst case is usually that "other outcome" case, not any of the backed selections — a classic exchange risk trap if you only scan bet selections.
- **Per-market locking is the right grain.** A live exchange runs many markets concurrently but each individual market sees comparatively few concurrent bets; guard each market's running totals with its own lock (not one global lock, not lock-free per-field, which risks torn reads across the four numbers).
- **BigDecimal throughout.** Stakes, odds, and every derived total use `BigDecimal` with `compareTo` — never `==` or `double` — money arithmetic with binary floats silently corrupts real liability figures.

## Common mistakes & senior signal
- Replaying the full bet list on every `pnlIfWins`/`worstCaseLiability` call instead of maintaining
  running totals — correct but doesn't scale, and misses the point of the kata.
- Only scanning backed/laid selections for the worst case and forgetting the "other outcome" — a
  pure-back book's real worst case is almost always that untested branch.
- One global lock across all markets instead of per-market locks — correct but needlessly serialises
  unrelated markets against each other.
- Updating the four running numbers with independent atomics instead of one lock per market — risks a
  reader observing a torn update (e.g. `backStakeTotal` bumped but `backOddsStake` not yet).
- Returning a negative "liability" for a market that can't lose money instead of clamping at zero.
- Using `double` anywhere in the stake/odds/pnl chain — silent corruption of real risk figures.

## Extensions
- **Stake limits** — reject `apply` once the resulting `worstCaseLiability` would exceed a configured
  cap, turning this from a passive tracker into an active risk gate.
- **Settlement** — once a market is settled, freeze its book and expose realised P&L instead of a
  hypothetical `pnlIfWins` for every outcome.
- **Sharded accumulators** — replace the single per-market lock with striped/atomic counters if
  profiling ever shows one market's lock as the bottleneck.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/positionkeeper/`)
- Java Interview Primer: Q48 (locks / `ConcurrentHashMap`), Q12 (`BigDecimal` for money)
