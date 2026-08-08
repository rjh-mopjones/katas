# Quote-Expiry (TTL) Cache

> Build the stale-price guard sitting in front of a live feed handler — every quote a trading desk reads must have been written inside the last N nanoseconds, or it's not read at all.

## The problem
Implement a cache of live quotes where every entry expires a fixed duration after it was *written*. This is a different eviction policy from an LRU or LFU cache: there is no capacity bound and no notion of "recently used" — an entry disappears purely because time passed, regardless of how often (or rarely) it was read. A quote nobody has touched for a while and a quote read a thousand times a second go stale at exactly the same instant.

## Requirements
- The cache can be built with the system nanosecond clock, or with an injected nanosecond clock for testing; the TTL must be positive.
- Storing a value for a key that already exists overwrites it and resets its expiry — the clock restarts from the moment of the overwrite.
- Reading a key returns the value only while it's still live; once expired it behaves as absent. Reading an expired entry must also purge it as a side effect (lazy purge, not a background sweep).
- The count of currently live entries must never include expired ones — expired entries are discovered and purged lazily, not on a timer.
- Checking whether a key exists follows the same liveness rule as reading it.
- Expiry boundary: an entry stored at time `t` is expired at query time `now` iff `now - t >= ttlNanos` — age exactly equal to the TTL counts as expired, not "just barely fresh."
- All operations must be safe under concurrent access from multiple threads, without relying on external locking by the caller.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/quotecache/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
