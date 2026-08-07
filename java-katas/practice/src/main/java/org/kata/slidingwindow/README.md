# Rolling Window Aggregator

> Aggregate a stream of timestamped values over a sliding time window — the primitive behind a rolling traded-volume, a moving-average price, or a rate limiter.

## The problem
Values arrive stamped with a millisecond timestamp. At any query time you report aggregates —
`count`, `sum`, weighted average — over only the values whose timestamps fall inside the last
`windowMillis`. Time is **injected** (`LongSupplier nowMillis`) so it is deterministic in tests: drive
it with an `AtomicLong`. Read methods evaluate lazily against the clock; there is no background thread.

## Requirements
- The window at query time `now` covers `now - windowMillis < ts <= now`: the trailing edge is
  **exclusive**, the leading edge **inclusive**. A value exactly `windowMillis` old has just expired.
- `add(ts, value)` and `add(ts, value, weight)` record a value; both return `false` (recording nothing)
  if `ts` is in the future or already past the trailing edge, `true` if it landed in the window.
- `count()` and `sum()` report over the current window; an empty window is `0` / `0.0`, never an NPE.
- `weightedAverage()` = `Σ(value·weight) / Σ(weight)` over the window, or an **empty** `OptionalDouble`
  when the window is empty or total weight is zero — never divide by zero.
- Events may arrive **out of timestamp order**. An out-of-order event still inside the window counts;
  one already past the trailing edge is rejected. Duplicate timestamps aggregate.
- `retainedBuckets()` exposes internal retention; it must stay **bounded by the window length**, no
  matter how many events arrive.

## What you implement
Implement `SlidingWindow` from scratch — the public API (`add` ×2, `count`, `sum`, `weightedAverage`,
`retainedBuckets`). You design the internal representation, the eviction strategy, and how reads stay
O(1) as values expire.

## The real challenge
- **Key by time, not arrival order.** A naive design that evicts "from the head" of an arrival-ordered
  queue assumes timestamps arrive monotonically — the first out-of-order event breaks it. Key by
  timestamp (a `TreeMap<Long, aggregate>`) and order-of-arrival stops mattering: late-but-in-window
  events merge, past-edge events are rejected, duplicates aggregate.
- **Running totals.** Recomputing `sum`/`count` by scanning on every read is O(n). Maintain running
  totals updated on every add and every eviction so reads are O(1) amortised — the value-sum and
  weight-sum must expire **together** or the weighted average drifts.
- **Bounded memory under high throughput.** Millions of events per second means you cannot keep one
  object per event. Aggregate into **per-timestamp (or per-bucket) totals**, not raw events: a million
  events in one millisecond collapse into a single bucket, so retention is O(window), not O(events).
  Name the granularity/accuracy trade-off — 1 ms buckets stay exact; coarser buckets shrink memory but
  blur to the bucket edge.
- **Floating point.** Assert sums with a delta (`assertEquals(expected, actual, 1e-9)`), never `==`.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/slidingwindow/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/slidingwindow/`
- Java Interview Primer: Q30 (TreeMap / sorted maps), Q155 (PriorityQueue), Q48 (lazy vs eager eviction)
