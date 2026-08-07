# Vending Machine

> Implement a vending machine state machine with coin management and exact-change dispensing.

## The problem
A vending machine holds a catalogue of products with prices, a limited stock count per product, and a physical coin float. A user inserts coins one at a time, selects a product by code, and receives the product plus exact change — or gets their money back if anything goes wrong. The machine must never short-change the customer.

## Requirements
- `insertCoin` accumulates the session balance; coins are tracked individually, not just as a sum.
- `select(code)` returns a sealed `DispenseResult` — one of: `Dispensed`, `InsufficientFunds`, `OutOfStock`, `UnknownProduct`, or `CannotMakeChange`. Expected failure modes are never thrown as exceptions.
- `InsufficientFunds` is the only non-terminal outcome: the session stays open so the user can insert more coins. All other failures auto-refund the inserted coins and reset the session.
- Exact change is computed against the projected inventory (float + just-inserted coins). If no valid coin combination can make exact change, the transaction is aborted before any state mutation.
- `refund()` returns the exact coins the user inserted (not equivalents from the float) and resets the session.
- All money is `BigDecimal` with 2 decimal places and `HALF_EVEN` rounding. `double` must not be used for any monetary value.
- All public methods are `synchronized` — the machine serves one user at a time.

## What you implement
Implement `VendingMachine` from scratch — the public API is `restock`, `loadCoins`, `insertCoin`, `select`, and `refund`. You design the internal state and any helper methods yourself.

(`Coin`, `Product`, and `DispenseResult` are provided as working fixtures.)

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/vending/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
