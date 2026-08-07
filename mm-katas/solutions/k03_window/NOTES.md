# Reference notes — Sliding Window Aggregator

`SlidingWindow.java` here passes Stage 1 → Stage 4. Prove it: `solutions/verify.sh 03`.

## One representation for every stage
A `TreeMap<Long, Bucket>` keyed by **millisecond timestamp**; each `Bucket` aggregates the `count`,
`sum`, `weight`, and `weighted` (Σ value·weight) of the events at that millisecond. Four **running
totals** mirror the live buckets so reads are O(1) amortised. `evict(now)` drops buckets past the
trailing edge (`ts <= now - windowMillis`), subtracting them from the totals, and runs on every add
and every read. The clock is an injected `LongSupplier`.

Why per-millisecond buckets are the whole trick: they're exact for millisecond-granular timestamps
*and* they bound memory. A million events in one millisecond collapse into a single bucket, so
retention is O(windowMillis) buckets regardless of event rate — that is the Stage-4 answer, and it also
serves Stages 1–3 exactly.

## Stage 1 → 2: trivial
Add `weight` and `weighted` to the bucket and to the running totals. `weightedAverage()` is
`totalWeighted / totalWeight`, or empty when `totalWeight == 0` — the divide-by-zero guard. Because the
totals are maintained incrementally, expiry keeps value-sum and weight-sum consistent for free.

## Stage 2 → 3: the real pivot
This is where a naive Stage-1 design dies. If you'd stored events in an arrival-ordered `ArrayDeque` and
evicted "from the head," an **out-of-order** arrival breaks you — the head is no longer the oldest by
timestamp, and a late event doesn't belong at the tail. Keying by **timestamp** (the `TreeMap`) makes
order-of-arrival irrelevant: an out-of-order-but-in-window event merges into its millisecond bucket; an
event already past the trailing edge is rejected (`ts <= now - windowMillis` → `add` returns `false`);
duplicate timestamps just aggregate. Nothing about the read path changes.

A candidate who reached for a `TreeMap`/time-keyed structure in Stage 1 barely changes anything here; one
who leaned on head-eviction has to rewrite.

## Stage 3 → 4: already there
Because a bucket is one millisecond of aggregate (not a list of events), memory is already
O(windowMillis): `retainedBuckets()` is `buckets.size()`, bounded by the window length. The Stage-4 test
pushes 1,000,000 events and asserts `retainedBuckets() <= windowMillis + 1` while `count()`/`sum()` stay
exact. In an interview you'd usually *pivot* here from per-event storage to bucketed aggregates; this
reference chose the bucketed representation up front, so Stage 4 is a no-op — and the NOTES call that out
so you can see how much cheaper the later stages are when Stage 1 didn't over-commit to arrival order.

**Accuracy vs granularity:** 1-ms buckets are exact for millisecond timestamps. Coarser buckets (say
1 s) shrink memory further but make count/sum accurate only to the bucket edge — the trade-off to name
out loud. `latenessMillis` is carried for the fixed-ring variant (a ring sized `window + lateness`); the
`TreeMap` version retains exactly the window and ignores it.

## Edge cases the tests pin
Empty window · trailing edge exclusive (`>`) · future event rejected · weighted average with zero total
weight (empty) · weights expiring with values · out-of-order in-window events counted · past-edge event
rejected · duplicate timestamps · interleaved out-of-order correctness as the clock advances · bounded
retention under 1,000,000 events · bucketed result matching a naive oracle.
