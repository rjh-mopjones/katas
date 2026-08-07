# Bank Account Service

## Approach
Build the single-threaded version first: `InMemoryAccountService` backed by a plain `HashMap<UUID, Account>`, with `Account` as an immutable record so every mutation is a functional replace (build a new `Account`, swap the map entry). `transfer` composes `withdraw` then `deposit` via `Optional.flatMap`, short-circuiting cleanly on insufficient funds. This nails down the business semantics — validation, insufficient-funds handling, transfer atomicity at the functional level — before any concurrency machinery gets involved.

`ConcurrentAccountService` is a mechanical hardening of the same rules. State lives in a `ConcurrentHashMap<UUID, Account>` (safe concurrent lookups/inserts), while a separate `ConcurrentHashMap<UUID, ReentrantLock>` holds one lock per account, created on demand via `computeIfAbsent` — atomic, so two threads racing to touch the same account for the first time still end up sharing a single lock instead of each getting their own. `deposit`/`withdraw` acquire the one relevant lock, mutate, and unlock in a `finally`.

`transfer` is the interesting case: it must hold both accounts' locks for the duration of the read-modify-write, and the order in which it acquires them must be globally consistent. Canonicalise by comparing `UUID`s and always locking `min(from, to)` first, then `max(from, to)`, regardless of transfer direction. Two threads doing `A→B` and `B→A` both contend for the same lock first — one wins, the other waits — so the cycle that causes deadlock can never form. Balances use `BigDecimal` throughout (never `double`), since IEEE-754 floats can't represent most decimal fractions exactly and money can't leak pennies to rounding error.

## The real challenge
- **Deadlock-free transfer via monotonic lock ordering.** Acquiring two locks for a transfer is unavoidable; the order must be globally consistent regardless of direction. Canonicalise by comparing `UUID`s: always lock `min(from, to)` first, then `max(from, to)`. This eliminates the cycle in the lock-acquisition graph that causes deadlock.
- **`computeIfAbsent` for per-account lock creation.** Two threads racing to open the same account must share one lock, not silently create two. `ConcurrentHashMap.computeIfAbsent` provides this atomically.
- **`BigDecimal`, not `double`, for money.** IEEE-754 cannot represent most decimal fractions exactly; pennies leak into rounding errors. Use `BigDecimal` and `signum()` for zero/negative checks.
- **Unlock in `finally`.** An exception inside the critical section must not leave the lock permanently held.

## Common mistakes & senior signal
- **Locking `from` then `to` in call order.** Looks correct in isolation but deadlocks the instant a second thread transfers in the opposite direction. A strong answer names the deadlock cycle explicitly (T1 holds `lock(A)` waiting on `lock(B)`, T2 holds `lock(B)` waiting on `lock(A)`) before reaching for a fix.
- **Creating a lock per call instead of per account id.** `new ReentrantLock()` inside `deposit`/`withdraw` protects nothing — every call gets its own lock. The lock must be looked up (and lazily created) keyed by account id, shared across all callers.
- **Racing lock creation itself.** Using `get`-then-`put` to lazily create the per-account lock is itself a race; `computeIfAbsent` is the atomic primitive for exactly this "create once, share always" pattern.
- **Reaching for `synchronized` on the whole map.** Correct but serialises every operation across every account — a strong candidate names this as the "single global lock" alternative and explains the throughput cost before rejecting it.
- **Using `double`/`float` for balances.** A classic tell; `BigDecimal` (with `signum()` for sign checks) is table stakes for any money-handling kata.
- **Forgetting `finally` around `unlock()`.** An exception mid-transfer must not leave a lock permanently held — that account becomes unusable forever, a self-inflicted denial of service.

## Extensions
- Swap `ReentrantLock#lock()` for `tryLock()` with a timeout and retry/back-off, and discuss the livelock risk that introduces versus the ordering approach.
- Discuss optimistic concurrency (STM-style compare-and-swap with retry) as a lock-free alternative under low contention.
- Discuss a single-writer thread with a command queue as a way to sidestep locking entirely.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/bank/`)
- Java Interview Primer: Q39 (synchronized block vs method), Q40 (deadlock), Q130 (BigDecimal for money)
