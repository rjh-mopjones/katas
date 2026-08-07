# Top-K Over a Stream

> Power the "biggest movers" panel on a live feed — the K most-traded selections, ranked and re-ranked as ticks arrive, with no full re-sort per update.

## The problem
Observations for many keys (selections, symbols, whatever the feed carries) arrive continuously, each nudging that key's cumulative score up or down. At any moment, a caller wants the current top K keys by score — highest first, deterministically ordered — without you re-scanning every key on every read.

## Requirements
- `TopK(int k)` — `k` must be non-negative; `k == 0` is legal and `top()` is then always empty. Reject a negative `k` with `IllegalArgumentException`.
- `add(String key, long weight)` adds `weight` to the key's cumulative score. `weight` may be negative — a key can fall as well as rise. A key is tracked from its first observation.
- `increment(String key)` is shorthand for `add(key, 1)`.
- `top()` returns up to `k` entries, highest score first, ties broken by key ascending (lexicographic) — deterministic regardless of arrival order. Fewer than `k` entries come back when fewer distinct keys exist; empty when `k == 0` or nothing has been added.
- `scoreOf(String key)` returns the key's current cumulative score, or `0` if it has never been observed.

## What you implement
Implement `TopK` from scratch — the public API is the constructor plus `add(String, long)`, `increment(String)`, `top()`, and `scoreOf(String)`. `Entry` (the `record` returned by `top()`) is already provided. You design the internal ranking structure yourself.

## The real challenge
- **Two structures, kept in sync**: a map of every key's current score is the source of truth; a second structure holds the same keys in ranked order. Every `add` must update both — and since a key's rank position depends on its score, updating the score means finding and moving that key's position in the ranked structure, not just overwriting a value.
- **Re-rank, don't re-sort**: a full re-sort per update is O(n log n) per tick, unaffordable on a hot feed. The efficient move is removing the key's *old* ranked position and re-inserting it at its new one — O(log n) — which means capturing the old score before you overwrite it in the map.
- **Deterministic tie-breaking**: two keys with equal scores must always compare the same way, or two readers polling `top()` a moment apart (or the same state read twice) see different orders for no reason. Break ties by key ascending, not insertion order or hash order.
- **Bounded top-K heap vs. exact tracking**: a fixed-size-K min-heap of "current leaders" is the textbook top-K structure and uses less memory when the key space vastly exceeds K — but it has no cheap way to find-and-rescore an arbitrary key that's ranked outside the heap already. Think through why an addressable/indexed heap (heap + key→index map) is needed to match this kata's "any key can move on any tick" requirement, and when the extra bookkeeping is or isn't worth it.
- **Count-Min Sketch + heap**: for a key space too large to track exactly (millions of symbols), an approximate sketch trades exactness for sublinear memory — worth naming as the extension when "exact" stops being affordable.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/topk/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/topk/`
- Java Interview Primer: Q155 (PriorityQueue / heaps), Q30 (TreeMap / sorted sets)
