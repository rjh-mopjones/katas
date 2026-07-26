# Position Book

> A trading desk's net-position keeper: many threads report fills across many symbols, and hedges move quantity between two symbols atomically — thread-safe, and no deadlock.

## The problem

Many order-handler threads report `Fill`s (signed: `+` bought, `-` sold) for many symbols. Keep the
running net position per symbol. A `hedge` moves quantity from one symbol to another atomically. This
is shared mutable state under heavy concurrent access.

## Requirements

- `apply(fill)` adds `fill.qty` to that symbol's net position (thread-safe).
- `position(symbol)` returns the current net (0 if unknown).
- `hedge(from, to, qty)` does `from -= qty` and `to += qty` as one atomic move.
- `total()` returns the sum of all positions (invariant under `hedge`).
- Correct under many threads calling `apply`/`hedge` simultaneously — no lost updates, no deadlock.

## What you implement

- `PositionBook::new()`, `apply(&self, &Fill)`, `hedge(&self, &str, &str, i64)`,
  `position(&self, &str) -> i64`, `total(&self) -> i64`

`Fill` is provided verbatim. You design the shared-state representation and the locking.

## The real challenge

- **Rust already stopped the data race.** You *cannot* mutate a shared `i64` from many threads without
  wrapping it (`Mutex`/atomic) — the borrow checker won't compile it. So the bugs left are the ones
  the compiler doesn't catch:
- **Lost update.** `apply` is a read-modify-write. Copy the value out, drop the lock, add, store back —
  and concurrent applies clobber each other. Hold the lock *across* the whole RMW.
- **Deadlock.** `hedge` locks *two* symbols. `hedge("A","B")` and `hedge("B","A")` running at once each
  grab their first lock and wait forever. Impose a **global lock order** (e.g. lock the smaller symbol
  name first), whichever way the hedge goes.
- **Granularity.** One big `Mutex<HashMap>` is simple and deadlock-free but serialises everything;
  per-symbol locks (`RwLock<HashMap<String, Arc<Mutex<i64>>>>`) give parallelism but create the
  deadlock hazard above. Know the trade-off; mention `AtomicI64`/sharding.
- **Money angle.** A lost fill is a wrong position and a mis-hedged book; a deadlock is a wedged
  trading system at peak.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` with a
**gated concurrency stress test**: use `std::thread::scope` + `std::sync::Barrier` to release many
threads together (max contention), then assert conservation (e.g. `total()` equals the sum of applied
quantities; opposing hedges keep `A + B` constant and don't hang). Then:

```
cd rust-katas && cargo test -p practice positionbook
```

## Reference

Worked solution: `rust-katas/solution/src/positionbook/`.

Extension: make `apply` lock-free with an `AtomicI64` per symbol; or shard the map to cut contention;
or return a `Result` surfacing mutex poisoning instead of `unwrap`.

Background: [The Rust Book — Shared-State Concurrency](https://doc.rust-lang.org/book/ch16-03-shared-state.html).
