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

## The real challenge
- **Deadlock-free transfer via monotonic lock ordering.** Acquiring two locks for a transfer is unavoidable; the order must be globally consistent regardless of direction. Canonicalise by comparing `UUID`s: always lock `min(from, to)` first, then `max(from, to)`. This eliminates the cycle in the lock-acquisition graph that causes deadlock.
- **`computeIfAbsent` for per-account lock creation.** Two threads racing to open the same account must share one lock, not silently create two. `ConcurrentHashMap.computeIfAbsent` provides this atomically.
- **`BigDecimal`, not `double`, for money.** IEEE-754 cannot represent most decimal fractions exactly; pennies leak into rounding errors. Use `BigDecimal` and `signum()` for zero/negative checks.
- **Unlock in `finally`.** An exception inside the critical section must not leave the lock permanently held.

## Run
```
mvn -pl practice test -Dtest=InMemoryAccountServiceTest,ConcurrentAccountServiceTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/bank/`
- Java Interview Primer: Q39 (synchronized block vs method), Q40 (deadlock), Q130 (BigDecimal for money)
