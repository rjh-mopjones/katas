# Circuit Breaker

## Approach
`CircuitBreaker` is a three-state machine (`CLOSED`, `OPEN`, `HALF_OPEN`) guarded by a single
`ReentrantLock`. In `CLOSED`, every failure increments a consecutive-failure counter and any
success resets it; hitting `failureThreshold` trips to `OPEN`. In `OPEN`, `call` fast-rejects with
`CircuitOpenException` without invoking the action at all. Once `openDurationNanos` has elapsed,
the breaker lazily advances to `HALF_OPEN` — evaluated only when `call()` or `state()` is next
invoked, not on a background timer. In `HALF_OPEN`, calls are forwarded as trials; each success
increments a trial counter, and hitting `successThreshold` closes the breaker, while a single
failure immediately reopens it and resets the open timer — there's no grace period for a trial
failure.

The lock is deliberately scoped narrowly: acquired to read/decide state, released before invoking
the (potentially slow) action, then re-acquired to record the outcome. Holding it across the
action call would serialize every concurrent caller and defeat the point of a circuit breaker. To
avoid a race where another thread trips the breaker between dispatch and outcome recording,
`recordSuccess`/`recordFailure` are given the state that was observed *at dispatch time*, not
whatever the current state happens to be when the outcome comes back.

A `ReentrantLock` was chosen over a CAS-based `AtomicReference` on an immutable state record: state
transitions are infrequent and held for microseconds, the action itself (network/DB call) dwarfs
lock overhead, and the lock-based code is far easier to review than a multi-field CAS state
machine — especially once the `HALF_OPEN` trial counter is factored in. The clock is injected as a
`LongSupplier` (`System::nanoTime` by default) specifically because wall-clock time
(`currentTimeMillis`) can jump backwards under NTP/DST adjustments, which would corrupt elapsed-time
math.

## The real challenge
- **Lock scope**: acquire the lock to check state and decide whether to invoke the action; release before calling the action; re-acquire to record the outcome. Holding the lock during the action would serialise all concurrent calls and eliminate the concurrency benefit.
- **State captured at call time**: `recordSuccess`/`recordFailure` receive the state observed when the call was dispatched, not the current state. This avoids a race where another thread trips the breaker between the action returning and the outcome being recorded.
- **`HALF_OPEN` failure is immediate**: any single failure in `HALF_OPEN` reopens the breaker and resets the open timer — there is no grace period.
- **Time-based transition is lazy**: `OPEN → HALF_OPEN` is only evaluated when `call()` or `state()` is invoked, not on a background timer. `maybeTransitionToHalfOpen()` must be called under the lock at both entry points.
- **Consecutive count vs rolling window**: this implementation counts consecutive failures (one success resets the counter). A rolling failure-rate window (e.g., Resilience4j's sliding window) is more nuanced but more complex — know the trade-off.

## Common mistakes & senior signal
- Holding the lock while invoking `action.call()` — the single most common mistake; it silently turns the breaker into a global serializing bottleneck even in `CLOSED` state.
- Re-reading `state` when recording the outcome instead of capturing it at dispatch — introduces a race where a concurrent trip mid-flight corrupts `HALF_OPEN` trial counting.
- Using `System.currentTimeMillis()` for the open-duration timer — a strong candidate names monotonicity as the reason `nanoTime`/injectable clock is required, unprompted.
- Evaluating `OPEN → HALF_OPEN` eagerly on a background thread instead of lazily at call time — works, but adds a scheduler/thread-pool lifecycle for no real benefit in this design; worth naming as a considered trade-off, not an oversight.
- Not resetting the trial-success counter on every transition into `HALF_OPEN`/`OPEN` — stale counters from a previous cycle silently shorten or lengthen the next probe window.

## Extensions
- Replace the consecutive-failure counter with a **rolling failure-rate window** (count- or time-based, à la Resilience4j) so a single stale failure doesn't reset an otherwise-healthy streak and vice versa.
- Add **metrics/events**: state-transition callbacks or counters exposing time-in-state and rejection counts, for observability.
- Compose with a **bulkhead** (bounded concurrent-call semaphore) or a **retry** decorator, and discuss ordering — retry should generally wrap the breaker, not the other way around.
- Support **per-dependency breaker registries** keyed by downstream name, so one slow/failing dependency doesn't need a hand-wired breaker instance per call site.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/circuitbreaker/`)
- Java Interview Primer: Q234 (circuit breaker), Q50 (CompletableFuture context), resilience patterns
