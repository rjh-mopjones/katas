# Interviewer script — Sliding Window Aggregator

Start the candidate on Stage 1. Every ~20 minutes, once it works, add the next requirement. The star
moment is Stage 3, where out-of-order arrival invalidates the naive Stage-1 eviction. Watch whether
they *see* the break before they hit it.

## Stage 1 — rolling count & sum
**Ask for:** `add(ts, value)`, `count()` and `sum()` over the last N ms.
**Push on:** "How do you know the current time?" (they should *inject* a clock — a `LongSupplier` — not
call `System.currentTimeMillis()`; it's the difference between testable and not). "A value exactly N ms
old — in or out?" (make them commit to a boundary rule and test it). "Empty window?"
**Strong:** injects the clock; a `Deque`/queue of `(ts, value)` with lazy eviction on read; boundary
rule stated; `isCloseTo` in tests.
**Weak:** `System.currentTimeMillis()` inline; recomputes sum by scanning a list each call; hand-waves
the boundary.

## Stage 2 — weighted average
**Bolt on:** "Now each value has a weight; give me the weighted average over the window."
**Push on:** "Empty window?" / "All weights zero?" (empty `OptionalDouble` — no divide-by-zero). "As a
value expires, both sums must drop — how do you keep them consistent?"
**Strong:** two running totals updated on add and on eviction; guards the zero-weight case.
**Weak:** recomputes both sums from scratch each call; NaN or exception on zero weight.

## Stage 3 — out-of-order events with a lateness bound
**Bolt on:** "Events can now arrive late — `add` may be called with a timestamp earlier than the last
one." This is the pivot. A Stage-1 design that evicts from the head assumed monotonic arrival.
**Push on:** "Your Stage-1 eviction popped the oldest at the front — does that still hold?" (no — an
out-of-order event isn't at the head). "A late event that's still in the window — does it count?"
(yes). "One that's already past the trailing edge?" (rejected). "Duplicate timestamps?"
**Strong:** immediately names the broken assumption ("head-eviction assumed ordered arrival"), moves to
a timestamp-keyed structure (`TreeMap<ts, agg>`) or a watermark; rejects past-edge events; handles
duplicates.
**Weak:** keeps popping the head and silently drops or mis-evicts late events; needs to be told the
assumption broke.

## Stage 4 — memory-bounded under high throughput
**Bolt on:** "This feed does a million events a second — you can't keep one object per event."
**Push on:** "What's your memory in terms of window and rate?" (must become O(window), not O(events)).
"How?" (aggregate into fixed sub-interval **buckets** — a ring of `window/bucket` slots — each holding
count/sum, not the raw events). "What's the cost?" (granularity: sub-bucket precision). "How do buckets
rotate as time moves?"
**Strong:** buckets/ring, O(window) memory, can state the accuracy trade-off and how rotation works;
can note that a 1-ms bucket keeps it exact while still bounding memory to the window length.
**Weak:** still stores every event; can't state the memory bound; conflates "bounded" with "a big
`ArrayList`."

**If they finish early:** ask for percentiles over the window (t-digest / bucketed histogram), or a
concurrent version (many producers), or make it a *count-based* window (last N events) instead of a
time window and discuss which structure changes.

## The reference in one line per stage
S1 injected clock + running totals + lazy eviction · S2 add weight totals, guard zero-weight · S3 a
`TreeMap<ts, bucket>` so out-of-order and eviction are by *time*, not arrival order · S4 the buckets are
already per-timestamp aggregates, so memory is O(window) — a million events collapse into the window's
buckets.
