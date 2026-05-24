# Vending Machine

> Implement a vending machine state machine with coin management and exact-change dispensing.

## The problem
A vending machine holds a catalogue of products with prices, a limited stock count per product, and a physical coin float. A user inserts coins one at a time, selects a product by code, and receives the product plus exact change — or gets their money back if anything goes wrong. The machine must never short-change the customer.

## Requirements
- `insertCoin` accumulates the session balance; coins are tracked individually, not just as a sum.
- `select(code)` returns a sealed `DispenseResult` — one of: `Dispensed`, `InsufficientFunds`, `OutOfStock`, `UnknownProduct`, or `CannotMakeChange`. Expected failure modes are never thrown as exceptions.
- `InsufficientFunds` is the only non-terminal outcome: the session stays open so the user can insert more coins. All other failures auto-refund the inserted coins and reset the session.
- Change is planned greedily (largest coin first) against the projected inventory (float + just-inserted coins). If greedy fails, the transaction is aborted before any state mutation.
- `refund()` returns the exact coins the user inserted (not equivalents from the float) and resets the session.
- All money is `BigDecimal` with 2 decimal places and `HALF_EVEN` rounding. `double` must not be used for any monetary value.
- All public methods are `synchronized` — the machine serves one user at a time.

## What you implement
Implement `VendingMachine` from scratch — the public API is `restock`, `loadCoins`, `insertCoin`, `select`, and `refund`. You design the internal state and any helper methods yourself.

(`Coin`, `Product`, and `DispenseResult` are provided as working fixtures.)

## The real challenge
- **Plan-then-commit**: `select` must compute the full change plan against a projected copy of the coin inventory and only mutate real state if the plan succeeds. If `planGreedyChange` returns `null`, the method must refund and return without touching stock or the float.
- **Greedy is only correct for canonical denominations**: the US set (PENNY, NICKEL, DIME, QUARTER, DOLLAR) allows greedy to always find the minimum-coin solution. For arbitrary denominations you would need DP min-coin instead.
- **CannotMakeChange vs InsufficientFunds**: a user may have paid more than the price yet still trigger `CannotMakeChange` when no coin combination in the projected inventory can make exact change — these are distinct failure modes.
- **Session lifecycle**: only `InsufficientFunds` preserves accumulated coin state; every other outcome (including success) calls `resetSession`.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/vending/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/vending/`
- Java Interview Primer: Q79/Q80 (state/patterns), Q130 (BigDecimal for money)
