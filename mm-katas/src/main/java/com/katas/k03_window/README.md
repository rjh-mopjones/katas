# Kata 03 · Sliding Window Aggregator

**Difficulty:** medium-hard · **Total target:** 90 min · **Class:** streaming & state

> Aggregate a stream of timestamped values over a sliding time window — the kind of thing behind a
> rolling volume, a moving average price, or a rate limiter. The interviewer keeps the window rolling
> and adds a harder requirement roughly every 20 minutes.

You implement `SlidingWindow`. The clock is **injected** (`LongSupplier nowMillis`) so time is
deterministic in tests — drive it with an `AtomicLong`. Run `./kata 03` to start the clock and reveal
Stage 1; later stages unlock as you pass each one.

The window covers the last `windowMillis`: a value with timestamp `ts` is in the window at query time
iff `now - windowMillis < ts <= now`. Read methods evaluate lazily against the clock.

## Stage 1 — rolling count & sum over N ms · target 20 min

- `add(ts, value)` records a value at time `ts`.
- `count()` and `sum()` report over the current window; values drop out as the clock advances.

Watch: an empty window (`0` and `0.0`, no NPE); a value **exactly** on the trailing edge — decide and
test the boundary (it's `>`, i.e. `now - windowMillis` is excluded); floating-point sums (assert with
`isCloseTo`, never `==`); a value timestamped in the future.

## Stage 2 — weighted average · target 20 min

- `add(ts, value, weight)` and `weightedAverage()` = `sum(value*weight) / sum(weight)` over the window.
- With no events, or total weight zero, return an **empty** `OptionalDouble` — don't divide by zero.

Watch: the two running sums (`sum(v*w)` and `sum(w)`) must expire **together** and stay consistent as
values leave the window.

## Stage 3 — out-of-order events with a lateness bound · target 25 min

Events no longer arrive in timestamp order. `add(ts, ...)` may be called with a `ts` earlier than a
previous one.

- An out-of-order event that is still inside the window must be counted.
- An event already past the window's trailing edge (`ts <= now - windowMillis`) is **rejected** —
  `add` returns `false`.

Watch: this breaks the obvious Stage 1 design. If you evicted "from the head" assuming timestamps
arrive in order, out-of-order arrivals land in the wrong place. Handle duplicate timestamps; keep
count/sum correct as the clock advances over interleaved arrivals.

## Stage 4 — memory-bounded under high throughput · target 25 min

Millions of events per second — you cannot keep one object per event.

- Aggregate so memory is **O(window)**, independent of the event rate: `retainedBuckets()` exposes how
  many retention buckets you hold, and it must stay bounded no matter how many events arrive.
- `count()`/`sum()` must stay correct.

Watch: bucket rotation as the clock advances; a value on a bucket boundary; the granularity vs accuracy
trade-off. The Stage 4 tests push a million events and assert both correctness and a bounded bucket
count.

## Run

```
./kata 03           # start: reveals Stage 1, starts the stopwatch
./kata 03 check     # run the current stage; on green, unlock + reveal the next stage
```

Reference (after you've worked it): `solutions/k03_window/` — `SlidingWindow.java` + `NOTES.md`.
