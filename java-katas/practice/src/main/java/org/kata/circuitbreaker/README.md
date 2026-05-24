# Circuit Breaker

> Implement the circuit breaker resilience pattern with a three-state machine and time-based recovery.

## The problem
Services that depend on a downstream system (database, external API) must stop hammering it when it starts failing. Build a circuit breaker that wraps arbitrary `Callable` actions: after a threshold of consecutive failures it opens and fast-rejects all calls; after a configured timeout it allows a small number of trial calls through to probe recovery; if the trials succeed it closes again.

## Requirements
- Three states: `CLOSED` (normal), `OPEN` (fast-reject), `HALF_OPEN` (trial probe).
- In `CLOSED`: every failure increments a consecutive-failure counter; any success resets it to zero. When the counter reaches `failureThreshold`, transition to `OPEN`.
- In `OPEN`: `call(action)` throws `CircuitOpenException` without invoking `action`. After `openDurationNanos` elapses, transition to `HALF_OPEN`.
- In `HALF_OPEN`: calls are forwarded. Each success increments a trial counter; when it reaches `successThreshold`, transition to `CLOSED`. The first failure immediately reopens the breaker and resets the open timer.
- The clock is injectable (`LongSupplier`) and must use monotonic time (`System::nanoTime`) — never `currentTimeMillis()`, which can jump backwards.
- The lock must not be held while invoking the action — actions can be slow (network calls) and holding the lock would serialise all callers.
- `state()` returns the current state (refreshing any pending `OPEN → HALF_OPEN` transition before returning).

## What you implement
Implement `CircuitBreaker` from scratch — the public API is `state()`, `call(Callable<T>)`, and two constructors (with and without an injectable `LongSupplier` clock). You design the internal state machine, locking strategy, and all transitions yourself. `CircuitOpenException` (the exception thrown when the breaker is OPEN) and the `State` enum (`CLOSED`, `OPEN`, `HALF_OPEN`) are provided as fully working types.

## The real challenge
- **Lock scope**: acquire the lock to check state and decide whether to invoke the action; release before calling the action; re-acquire to record the outcome. Holding the lock during the action would serialise all concurrent calls and eliminate the concurrency benefit.
- **State captured at call time**: `recordSuccess`/`recordFailure` receive the state observed when the call was dispatched, not the current state. This avoids a race where another thread trips the breaker between the action returning and the outcome being recorded.
- **`HALF_OPEN` failure is immediate**: any single failure in `HALF_OPEN` reopens the breaker and resets the open timer — there is no grace period.
- **Time-based transition is lazy**: `OPEN → HALF_OPEN` is only evaluated when `call()` or `state()` is invoked, not on a background timer. `maybeTransitionToHalfOpen()` must be called under the lock at both entry points.
- **Consecutive count vs rolling window**: this implementation counts consecutive failures (one success resets the counter). A rolling failure-rate window (e.g., Resilience4j's sliding window) is more nuanced but more complex — know the trade-off.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/circuitbreaker/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/circuitbreaker/`
- Java Interview Primer: Q234 (circuit breaker), Q50 (CompletableFuture context), resilience patterns
