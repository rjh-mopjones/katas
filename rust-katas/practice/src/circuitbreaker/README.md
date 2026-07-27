# Circuit Breaker

> A resilience wrapper in front of a flaky downstream: after repeated failures it *trips open* and fast-fails every call — freeing callers instead of hammering a dead dependency — then probes for recovery.

## The problem

A service calls a flaky downstream (a payment gateway, a pricing API, a venue). When that dependency
starts failing, retrying every call makes things worse: threads pile up on slow or dead sockets,
latency spikes, and the caller drags itself down with the dependency. A circuit breaker watches the
failure stream and, after enough consecutive failures, **trips open** and *fast-fails* subsequent
calls without touching the downstream. After a `cooldown` it lets a few probe calls through; if they
succeed it **closes** again, if one fails it re-opens.

## Requirements

- **`Closed`** (normal): `call` runs `f`. Each `Err` increments a *consecutive*-failure counter; each
  `Ok` resets it to `0`. When the counter reaches `failure_threshold`, transition to **`Open`** and
  record the time (via the injected clock).
- **`Open`** (tripped): `call` returns `Err(CallError::Open)` **without** invoking `f`. Once
  `cooldown` has elapsed since it opened (per the clock), the next `call`/`state()` moves it to
  **`HalfOpen`**.
- **`HalfOpen`** (probing): `call` runs `f`. Each `Ok` increments a success counter; when it reaches
  `success_threshold`, transition to **`Closed`** (reset counters). The *first* `Err` in `HalfOpen`
  re-opens it and restarts the timer.
- Thread-safe: guard all state with a `Mutex`. The clock is injected so time is deterministic in
  tests — **no real sleeps**.

## What you implement

- `CircuitBreaker::new(failure_threshold, success_threshold, cooldown, clock)` — `clock` is any
  `Fn() -> Instant + Send + Sync + 'static`.
- `state() -> State` — the current state, after applying any pending cooldown expiry.
- `call<T, E>(f) -> Result<T, CallError<E>>` — run `f` through the breaker.

`State` (`Closed`/`Open`/`HalfOpen`) and `CallError<E>` (`Open` / `Inner(E)`) are provided.

## The real challenge

- **A three-state machine as an `enum` + `match`.** Model the current state as `State` and dispatch
  every transition with `match`. Encoding it this way makes illegal states unrepresentable — no
  tangle of `is_open` / `opened_at` booleans that can drift out of sync.
- **Thread-safe state behind a `Mutex`.** One breaker is shared across worker threads; put the state,
  the counters, and the open-timestamp in a single `Mutex<Inner>` and mutate them under one lock so a
  transition is atomic. Consecutive-count semantics *require* it — two threads must not both see
  "one below threshold" and both trip.
- **Inject the clock so time is testable.** Store it as `Box<dyn Fn() -> Instant + Send + Sync>`.
  Production passes `Instant::now`; tests pass a closure reading a shared, hand-advanced `Instant` and
  jump the cooldown forward with zero real waiting.
- **`CallError` separates fast-fail from a real failure.** `CallError::Open` means `f` never ran (shed
  load / serve a fallback); `CallError::Inner(e)` preserves the downstream error `f` returned.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` using a
**fake clock** (`let t = Arc::new(Mutex::new(Instant::now()));` and a clock closure
`{ let t = t.clone(); move || *t.lock().unwrap() }`; advance with
`*t.lock().unwrap() += Duration::from_secs(..)`). Cover: closed passes through and returns `Ok`; N
consecutive failures trip to `Open`; in `Open` the wrapped `f` is **not** invoked (track it with an
`AtomicUsize`) and it returns `CallError::Open`; after cooldown the state becomes `HalfOpen`;
`success_threshold` successes in `HalfOpen` close it; a failure in `HalfOpen` re-opens it; a success in
`Closed` resets the failure count (threshold-1 failures then a success then more failures doesn't trip
early). Then:

```
cd rust-katas && cargo test -p practice circuitbreaker
```

## Reference

Worked solution: `rust-katas/solution/src/circuitbreaker/`.

Extension: trip on a **rolling-window failure rate** (e.g. >50% of the last N calls) instead of a
consecutive count; add a per-call timeout that counts a slow call as a failure; or expose metrics
(trip count, time-in-state).

Background: [The Rust Book — enums and pattern matching](https://doc.rust-lang.org/book/ch06-00-enums.html)
and [`std::sync::Mutex`](https://doc.rust-lang.org/std/sync/struct.Mutex.html).
