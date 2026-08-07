# Quote-Expiry (TTL) Cache

> Build the stale-price guard sitting in front of a live feed handler — every quote a trading desk reads must have been written inside the last N nanoseconds, or it's not read at all.

## The problem
Implement a cache of live quotes where every entry expires a fixed duration after it was *written*. This is a different eviction policy from an LRU or LFU cache: there is no capacity bound and no notion of "recently used" — an entry disappears purely because time passed, regardless of how often (or rarely) it was read. A quote nobody has touched for a while and a quote read a thousand times a second go stale at exactly the same instant.

## Requirements
- `QuoteCache(long ttlNanos)` uses the system nanosecond clock.
- `QuoteCache(long ttlNanos, LongSupplier clock)` takes an injected clock (nanos); `ttlNanos` must be positive.
- `put(K key, V value)` stores or overwrites an entry, stamping the current time. Overwriting an existing key **resets** its TTL.
- `get(K key)` returns the value if present and not expired, else `Optional.empty()`. An expired entry must be removed as a side effect of this call (lazy purge).
- `size()` returns the number of currently **live** entries — expired entries must not be counted, and must be purged lazily as they're discovered.
- `containsKey(K key)` returns true iff a live entry exists for that key.
- Expiry boundary: an entry stored at time `t` is expired at query time `now` iff `now - t >= ttlNanos` — age exactly equal to the TTL counts as expired, not "just barely fresh."

## What you implement
Implement `QuoteCache<K, V>` from scratch — the public API is two constructors (`ttlNanos` alone, and `ttlNanos` + injected `LongSupplier` clock), `put(K, V)`, `get(K)`, `size()`, and `containsKey(K)`. You design the internal storage and the expiry/purge mechanism yourself, and it must be safe under concurrent `put`/`get` from multiple threads without external locking.

## The real challenge
- **Time-based eviction is not LRU/LFU**: there's no capacity, no recency list, no access-frequency counter — just a stamped write time compared against the clock on every read. Don't reach for a linked-list-plus-hashmap design; that machinery solves a different problem.
- **The injected clock**: never call `System.nanoTime()` inline in the cache logic — take a `LongSupplier` (defaulting to `System::nanoTime`) so a test can drive time deterministically with an `AtomicLong`, including landing exactly on the expiry boundary.
- **The exact-TTL boundary**: decide (and test) whether `now - storedAt == ttlNanos` is expired or still live, and be consistent about it everywhere the check happens (`get`, `size`, `containsKey`).
- **Lazy vs. active expiry**: lazy expiry (check-and-purge on access) keeps `put` O(1) with zero background bookkeeping, but a key nobody ever reads again lingers in memory forever. Know the trade-off and be ready to name the alternative (a reaper thread, a `DelayQueue`, a `ScheduledExecutorService`).
- **The conditional-remove race**: if a reader finds an entry expired and a writer concurrently overwrites that same key with a fresh value, an unconditional `remove(key)` can delete the writer's fresh entry. Use the map's compare-and-remove primitive (remove-only-if-still-mapped-to-this-exact-value) so a stale read never clobbers a fresh write.
- **Thread-safety without a lock**: a `ConcurrentHashMap` of immutable per-entry (value, storedAt) records gets you lock-free `put`/`get`; the only place correctness gets subtle is the expiry-triggered remove above.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/quotecache/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/quotecache/`
- Java Interview Primer: Q241 (ConcurrentHashMap atomic primitives — computeIfAbsent/remove(k,v)), Q48 (injected clocks for deterministic time-based tests)
