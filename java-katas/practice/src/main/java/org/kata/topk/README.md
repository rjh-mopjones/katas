# Top-K Over a Stream

> Power the "biggest movers" panel on a live feed — the K most-traded selections, ranked and re-ranked as ticks arrive, with no full re-sort per update.

## The problem
Observations for many keys (selections, symbols, whatever the feed carries) arrive continuously, each nudging that key's cumulative score up or down. At any moment, a caller wants the current top K keys by score — highest first, deterministically ordered — without you re-scanning every key on every read.

## Requirements
- Constructing the structure takes a maximum size `k`, which must be non-negative; a `k` of zero
  is legal, and the top entries are then always empty. A negative `k` is rejected with
  `IllegalArgumentException`.
- Adding a weighted observation for a key adds that weight to the key's cumulative score; the
  weight may be negative, so a key's score can fall as well as rise. A key is tracked from its
  first observation.
- There is a shorthand for adding an observation of weight one to a key.
- Querying the top entries returns up to `k` of them, highest score first, with ties broken by key
  in ascending (lexicographic) order — deterministic regardless of arrival order. Fewer than `k`
  entries come back when fewer distinct keys exist; the result is empty when `k` is zero or nothing
  has been added yet.
- Looking up a key's current cumulative score returns `0` if it has never been observed.

## What you're given
- `Entry` — a record pairing a key with its cumulative score, as returned when querying the top entries.

You design the entire public API — method names, parameters, return types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/topk/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
