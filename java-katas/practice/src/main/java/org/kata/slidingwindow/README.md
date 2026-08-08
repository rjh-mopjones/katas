# Rolling Window Aggregator

> Aggregate a stream of timestamped values over a sliding time window — the primitive behind a rolling traded-volume, a moving-average price, or a rate limiter.

## The problem
Values arrive stamped with a millisecond timestamp. At any query time you report aggregates —
count, sum, weighted average — over only the values whose timestamps fall inside the last window
of time. The current time is injected rather than read from the system clock, so behaviour is
deterministic in tests. Reads evaluate lazily against the clock; there is no background thread.

## Requirements
- The window at query time `now` covers `now - windowMillis < ts <= now`: the trailing edge is
  **exclusive**, the leading edge **inclusive**. A value exactly `windowMillis` old has just expired.
- Recording a value reports whether it landed in the window: it is rejected (and not recorded) if
  its timestamp is in the future or already past the trailing edge. There is a variant that also
  takes a weight, for later use in the weighted average.
- The count and the sum report over the current window; an empty window yields zero for both,
  never a null-pointer exception.
- The weighted average is `Σ(value·weight) / Σ(weight)` over the window, reported as an **empty**
  result when the window is empty or the total weight is zero — never a divide-by-zero.
- Events may arrive **out of timestamp order**. An out-of-order event still inside the window counts;
  one already past the trailing edge is rejected. Duplicate timestamps aggregate.
- You must expose a way to observe how many internal units the implementation is retaining to
  represent the window; that count must stay **bounded by the window length**, no matter how many
  events arrive — it must not grow with the number of events received.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/slidingwindow/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
