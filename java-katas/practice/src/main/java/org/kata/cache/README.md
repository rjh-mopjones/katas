# LRU / LFU Cache

> Implement O(1) LRU and LFU caches from scratch, then make the LRU thread-safe.

## The problem
Build two bounded in-memory caches — one with a Least Recently Used eviction policy and one with a Least Frequently Used policy — both with O(1) get and put. Then wrap the LRU cache in a thread-safe variant that can handle concurrent reads and writes without corruption.

## Requirements
- `get(key)` returns `Optional.empty()` on a miss. On a hit, LRU promotes the key to most-recently-used; LFU increments its frequency count.
- `put(key, value)` on an existing key updates the value and promotes/increments without eviction and without changing the cache size.
- `put(key, value)` on a new key when the cache is full evicts exactly one entry before inserting.
- LRU evicts the least recently used entry (tail of the recency list).
- LFU evicts the entry with the lowest frequency; ties are broken by least-recently-used within that frequency bucket.
- `size()` returns the current entry count, always in `[0, capacity]`.
- `clear()` removes all entries and resets internal state.
- `ConcurrentLruCache` must be safe for concurrent `get` and `put` from multiple threads.

## What you implement
Implement `LruCache`, `LfuCache`, and `ConcurrentLruCache` from scratch — the `Cache` public API (`get`, `put`, `size`, `clear`). You design the internal data structures yourself.

(`Cache` is provided as a working fixture.)

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/cache/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
