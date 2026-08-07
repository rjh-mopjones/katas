# Rolling Window Aggregator

## Approach
The core move is **keying storage by timestamp, not by arrival order**: a `TreeMap<Long, Bucket>`
where each `Bucket` holds a per-millisecond aggregate (`count`, `sum`, `weight`, `weighted = Σ
value·weight`). Because the map is keyed by *when a value belongs*, not *when it arrived*, an
out-of-order-but-in-window event just merges into its millisecond's bucket, a duplicate timestamp
aggregates, and a value already past the trailing edge is rejected outright — nothing on the read
path cares what order values showed up in.

Four running totals (`totalCount`, `totalSum`, `totalWeight`, `totalWeighted`) mirror the live
buckets so `count()`, `sum()`, and `weightedAverage()` are O(1) amortised instead of an O(n) rescan
per read. A single `evict(now)` — called on every `add` and every read — walks `buckets.headMap(edge,
true)` (edge = `now - windowMillis`) and subtracts each expired bucket from the four totals before
removing it. There is no background thread; eviction is purely lazy, driven by whoever next touches
the window.

The window boundary is `now - windowMillis < ts <= now`: trailing edge **exclusive**, leading edge
**inclusive** — a value exactly `windowMillis` old has just expired. This is implemented as
`buckets.headMap(edge, true)` for eviction and the `tsMillis <= nowMs - windowMillis` guard in `add`.

Because a bucket aggregates *all* values sharing a millisecond rather than storing one object per
value, retention is bounded by `O(windowMillis)` buckets regardless of event *rate* — a million
events in the same millisecond collapse into a single bucket. `retainedBuckets()` exposes the live
`buckets.size()` so that bound is directly testable.

## The real challenge
- **Key by time, not arrival order.** A naive design that evicts "from the head" of an arrival-ordered
  queue assumes timestamps arrive monotonically — the first out-of-order event breaks it. Keying by
  timestamp (a `TreeMap<Long, aggregate>`) makes order-of-arrival stop mattering: late-but-in-window
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

## Common mistakes & senior signal
- **Off-by-one on the window boundary** — treating both edges as inclusive (or both exclusive) is a
  silent data bug, not a crash. State the `now - windowMillis < ts <= now` rule explicitly and write a
  test for a value landing exactly on the trailing edge.
- **Evicting by insertion/arrival order** instead of by key — breaks the instant an event arrives late.
  Recognizing this trap and choosing a sorted-by-time structure up front is the main signal.
- **Letting the weighted-average totals drift** — updating `totalSum`/`totalCount` on add/evict but
  forgetting `totalWeight`/`totalWeighted` (or vice versa) desyncs the average from the raw totals.
- **Forgetting the divide-by-zero case** for `weightedAverage()` on an empty window or all-zero-weight
  window — should return `OptionalDouble.empty()`, not throw or return `NaN`.
- **Storing one object per event** rather than aggregating into buckets — works in tests, falls over
  under real throughput; naming the O(events) vs O(window) trade-off out loud is senior signal.
- **Comparing floating-point sums with `==`** instead of an epsilon delta.

## Extensions
- **Percentiles** over the window — swap the scalar bucket for a bucketed histogram or t-digest.
- **Count-based window** (last N events) instead of time-based — the structure changes to a
  ring/deque of events since the boundary is now ordinal, not temporal.
- **Concurrency** — many producers; guard the totals + map with a single lock, or shard by key and
  merge on read.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/slidingwindow/`)
- Java Interview Primer: Q30 (TreeMap / sorted maps), Q155 (PriorityQueue), Q48 (lazy vs eager eviction)
