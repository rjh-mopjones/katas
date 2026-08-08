# LRU / LFU Cache

> Implement O(1) LRU and LFU caches from scratch, then make the LRU implementation thread-safe.

## The problem
Build two bounded in-memory caches — one with a Least Recently Used eviction policy and one with
a Least Frequently Used policy — both with O(1) lookup and insert. The LRU implementation must
additionally be safe for concurrent reads and writes without corruption.

## Requirements
- Looking up a missing key returns nothing; looking up an existing key returns its value — for
  the LRU policy this also counts as a "use" that refreshes recency, for the LFU policy it
  increments the key's frequency count.
- Inserting a key that already exists overwrites its value and refreshes its recency/frequency,
  without evicting anything or changing the cache size.
- Inserting a new key when the cache is already at capacity evicts exactly one entry before the
  new entry is inserted.
- The LRU policy evicts the least recently used entry.
- The LFU policy evicts the entry with the lowest frequency; ties are broken by least-recently-used
  within that frequency bucket.
- The current entry count is always within `[0, capacity]` and can be queried.
- All entries can be cleared in one call, resetting internal state.
- The LRU implementation must be safe for concurrent lookups and inserts from multiple threads.

## What you're given
`Cache<K, V>` interface is provided as a working fixture — both implementations satisfy it.

You design the entire public API — method names, parameters, return types — and the internals
from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/cache/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
