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

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/quotecache/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
