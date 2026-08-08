# Bank Account Service

> Build a thread-safe in-memory banking service that handles concurrent transfers without deadlocking.

## The problem
Model a simple bank with accounts that support deposits, withdrawals, and transfers. Start with a correct single-threaded implementation, then harden it into a concurrent one. The transfer operation is the crux: money must never vanish or double-count, and two threads doing opposite transfers (`A→B` and `B→A`) must not deadlock each other.

## Requirements
- Opening an account requires a non-negative opening balance.
- Deposit and withdrawal amounts must be strictly positive; negative or zero amounts are rejected with `IllegalArgumentException`.
- A withdrawal that exceeds the available balance is reported as an absent result, not an exception.
- Transferring between accounts moves nothing and reports failure if either account is missing or the source has insufficient funds. Transferring an account to itself also reports failure.
- The concurrent implementation must be safe under high thread contention: concurrent deposits, withdrawals, and transfers across many accounts produce no lost updates, no negative balances, and no deadlocks.

## What you're given
`Account` (an immutable record holding an id and balance, validated non-negative at construction,
with a functional-update method for producing a new balance) and `AccountService` (the interface
both implementations conform to) are provided as fully working scaffolding. You design the entire
public API — method names, parameters, return types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/bank/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
