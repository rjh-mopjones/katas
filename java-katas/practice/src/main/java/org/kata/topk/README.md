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

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/topk/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
