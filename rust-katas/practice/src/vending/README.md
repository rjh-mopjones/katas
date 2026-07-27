# Vending Machine

> The classic state-machine LLD problem: insert coins, pick a slot, get the product plus correct change — or a precise error.

## The problem

Build a vending machine. A customer inserts coins to build a balance, then selects a slot. If the
product is in stock and the balance covers its price, the machine dispenses it *and* the right change
from its coin float, then resets. Otherwise it returns a precise error.

## Requirements

- `insert(coin)` adds the coin's value to the balance; `balance()` returns the current inserted amount.
- `add_coins(&[Coin])` seeds the machine's change float; `stock(slot, product, price, qty)` loads a slot.
- `select(slot)`:
  - unknown slot or out of stock → `Err(SoldOut)`.
  - `balance < price` → `Err(InsufficientFunds { needed, balance })`, nothing changes.
  - else make change = `balance - price` **greedily** from the float; if it can't be made exactly →
    `Err(ExactChangeOnly)`, nothing changes. Otherwise dispense: decrement stock, hand back the change,
    reset the balance, and return `Dispensed { product, change }`.
- `refund()` returns the inserted coins and resets the balance.

## What you implement

- `VendingMachine`: `new`, `stock`, `add_coins`, `insert`, `balance`, `select`, `refund` (+ `Default`).

`Coin` (with `cents`), `VendError`, `Dispensed` are provided. You design the storage.

## The real challenge

- **A state machine over balance + inventory + coin float.** `select` is a `match`/guard cascade that
  must fail (sold out / insufficient / no change) *before* mutating anything — a purchase either fully
  commits or leaves the machine untouched.
- **A data-carrying error enum.** `InsufficientFunds { needed, balance }` returns context, not just a
  tag — errors are values.
- **Greedy change-making** over `5/10/25`. It's optimal for canonical denominations with enough supply,
  but a limited float can make greedy fail where a solution exists (it grabs a quarter, then can't make
  the last nickel) — real machines then say "exact change only". A coin-change DP is the robust fix.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` (exact
money → no change, change given, insufficient funds leaves state intact, sold out, exact-change-only,
refund), then:

```
cd rust-katas && cargo test -p practice vending
```

## Reference

Worked solution: `rust-katas/solution/src/vending/`.

Extension: the **typestate** pattern (make phases distinct types so `select`-before-`insert` won't
compile); or a DP change-maker that never spuriously reports `ExactChangeOnly`.

Background: [The Rust Book — enums & `match`](https://doc.rust-lang.org/book/ch06-02-match.html).
