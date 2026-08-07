# Vending Machine

## Approach
`select` walks the failure modes in order of cheapness — unknown product, out of stock, insufficient
funds, can't-make-change — and represents every outcome as a variant of a sealed `DispenseResult`
rather than an exception. Expected business outcomes aren't exceptional control flow; the compiler
enforces exhaustive handling on switch.

The coin float lives in an `EnumMap<Coin, Integer>` rather than a `HashMap`: the key set is fixed and
known (`PENNY`..`DOLLAR`), so the map is a dense array indexed by ordinal — no hashing, no buckets,
cache-friendly, and iteration order matches declaration order for free.

`select` is **plan-then-commit**: it builds `projectedInventory` (the current float plus the coins
just inserted this session) and runs `planGreedyChange` against that *copy*. Only once a viable plan
exists does it mutate the real `coinInventory` and `stock`. If planning fails, the method walks away
having touched nothing but the refund — the same shape as git's index/working-tree split or a small
2-phase commit: validate everything before any side effect lands.

Change-making itself is greedy: largest denomination first, take as many as fit, recurse on the
remainder. This is optimal because the denominations are canonical (1, 5, 10, 25, 100 cents) — every
value above the next-smaller coin's threshold is reachable without overshoot.

Concurrency is a single `synchronized` per public method. A physical machine serves one human at a
time, so a coarse intrinsic lock costs nothing under real contention and gives atomic
insert/select/refund sessions for free — no need for finer-grained locking here.

## The real challenge
- **Plan-then-commit**: `select` must compute the full change plan against a projected copy of the
  coin inventory and only mutate real state if the plan succeeds. If `planGreedyChange` returns
  `null`, the method must refund and return without touching stock or the float.
- **Greedy is only correct for canonical denominations**: the US set (PENNY, NICKEL, DIME, QUARTER,
  DOLLAR) allows greedy to always find the minimum-coin solution. For arbitrary denominations you
  would need DP min-coin instead.
- **CannotMakeChange vs InsufficientFunds**: a user may have paid more than the price yet still
  trigger `CannotMakeChange` when no coin combination in the projected inventory can make exact
  change — these are distinct failure modes.
- **Session lifecycle**: only `InsufficientFunds` preserves accumulated coin state; every other
  outcome (including success) calls `resetSession`.

## Common mistakes & senior signal
- **Mutating state before change planning succeeds.** The naive approach decrements stock and the
  float as it dispenses coins, then has no clean way to unwind if it runs out of coins partway
  through. A strong answer separates planning (pure, over a copy) from committing (side effects only
  after the plan is known to work).
- **Using `double` for money.** `0.10` has no exact binary representation, so repeated addition drifts.
  `BigDecimal` with a fixed scale and `HALF_EVEN` rounding is the only defensible choice for a
  candidate to reach for unprompted.
- **Treating greedy as universally optimal.** A candidate who states *why* greedy works here
  (canonical denominations) rather than just applying it shows they understand the algorithm's
  precondition, not just its mechanics.
- **Collapsing `InsufficientFunds` into the other failures.** It's the only outcome that must *not*
  reset the session — missing this breaks the "keep inserting coins" UX a real machine needs.
- **Forgetting the inserted coins count as available change stock.** The user's coins are physically
  inside the machine once accepted, so they must be added to the projected inventory before planning
  change — otherwise low-denomination change (e.g. all pennies) can spuriously fail.

## Extensions
- **Distributed inventory.** Multiple machines sharing one logical stock pool — move state to a
  shared store, replace `synchronized` with optimistic CAS or a reservation service, and treat each
  machine as a stateless client.
- **Cashless payments.** Adds new states to the session machine: `Authorising` (waiting on the PSP),
  `Authorised` (hold placed), `Captured` (charged on dispense), `Voided` (released on failure). The
  plan-then-commit pattern carries over directly: only capture the payment once you've verified you
  can dispense.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/vending/`)
- Java Interview Primer: Q79/Q80 (state/patterns), Q130 (BigDecimal for money)
