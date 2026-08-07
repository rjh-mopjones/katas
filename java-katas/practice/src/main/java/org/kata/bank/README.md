# Bank Account Service

> Build a thread-safe in-memory banking service that handles concurrent transfers without deadlocking.

## The problem
Model a simple bank with accounts that support deposits, withdrawals, and transfers. Start with a correct single-threaded implementation, then harden it into a concurrent one. The transfer operation is the crux: money must never vanish or double-count, and two threads doing opposite transfers (`A→B` and `B→A`) must not deadlock each other.

## Requirements
- `open(openingBalance)` creates a new account; opening balance must be non-negative.
- `deposit` and `withdraw` amounts must be strictly positive; negative or zero throws `IllegalArgumentException`.
- `withdraw` returns `Optional.empty()` (not an exception) when funds are insufficient.
- `transfer` returns `false` — and moves nothing — if either account is missing or the source has insufficient funds. Transferring to the same account returns `false`.
- `ConcurrentAccountService` must be safe under high thread contention: concurrent deposits, withdrawals, and transfers across many accounts produce no lost updates, no negative balances, and no deadlocks.

## What you implement
Implement `InMemoryAccountService` and `ConcurrentAccountService` from scratch — the public API (`open`, `find`, `deposit`, `withdraw`, `transfer`). You design the internal data structures, field declarations, and helper methods yourself.

(`Account` record and `AccountService` interface are provided as fully working scaffolding.)

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/bank/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
