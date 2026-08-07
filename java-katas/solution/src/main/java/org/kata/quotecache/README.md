# Quote-Expiry (TTL) Cache

## Approach
Store each key against an immutable `(value, storedAt)` record in a single `ConcurrentHashMap`. A write is just `map.put(key, new Entry(value, clock))` — O(1), no auxiliary index, and overwriting a key naturally resets its TTL because the new record carries a fresh timestamp. There is no capacity list, no recency ordering, no frequency counter: unlike an LRU/LFU cache, nothing about *access* affects lifetime, so none of that machinery is needed.

Expiry is **lazy**. Every accessor (`get`, `size`, `containsKey`) compares the entry's age against `ttlNanos` at read time and purges it on the spot if stale. This keeps `put` O(1) with zero background bookkeeping — no timer wheel, no priority queue maintained on every write — at the cost of letting a never-read-again key linger in the map until something touches it. `containsKey` is just `get(key).isPresent()`, so all three read paths share one expiry check.

Time comes from an injected `LongSupplier` of nanoseconds (defaulting to `System::nanoTime`) rather than an inline `System.nanoTime()` call, so a test can drive time deterministically with an `AtomicLong` and land exactly on the expiry boundary. The boundary is **inclusive** of expiry: `now - storedAt >= ttlNanos` means an entry exactly `ttlNanos` old is already stale — the stricter, safer reading for a price feed ("valid for up to N nanos", not "more than N nanos"), and it's applied identically in `get`, `size`, and `containsKey`.

Concurrency rides on the `ConcurrentHashMap`, so many-threaded `put`/`get` needs no external lock. The one subtlety is the expiry-triggered remove: it uses the conditional `remove(key, expiredEntry)` (remove-only-if-still-mapped-to-this-exact-record) so a reader that observed a stale snapshot can never delete a fresher write that a concurrent `put` slipped in.

## The real challenge
- **Time-based eviction is not LRU/LFU**: there's no capacity, no recency list, no access-frequency counter — just a stamped write time compared against the clock on every read. Don't reach for a linked-list-plus-hashmap design; that machinery solves a different problem.
- **The injected clock**: never call `System.nanoTime()` inline in the cache logic — take a `LongSupplier` (defaulting to `System::nanoTime`) so a test can drive time deterministically with an `AtomicLong`, including landing exactly on the expiry boundary.
- **The exact-TTL boundary**: decide (and test) whether `now - storedAt == ttlNanos` is expired or still live, and be consistent about it everywhere the check happens (`get`, `size`, `containsKey`).
- **Lazy vs. active expiry**: lazy expiry (check-and-purge on access) keeps `put` O(1) with zero background bookkeeping, but a key nobody ever reads again lingers in memory forever. Know the trade-off and be ready to name the alternative (a reaper thread, a `DelayQueue`, a `ScheduledExecutorService`).
- **The conditional-remove race**: if a reader finds an entry expired and a writer concurrently overwrites that same key with a fresh value, an unconditional `remove(key)` can delete the writer's fresh entry. Use the map's compare-and-remove primitive (remove-only-if-still-mapped-to-this-exact-value) so a stale read never clobbers a fresh write.
- **Thread-safety without a lock**: a `ConcurrentHashMap` of immutable per-entry (value, storedAt) records gets you lock-free `put`/`get`; the only place correctness gets subtle is the expiry-triggered remove above.

## Common mistakes & senior signal
- **Reaching for an LRU-style linked list.** Wiring a recency list plus a hashmap solves eviction-by-access, not eviction-by-time — it's dead complexity here. The senior signal is recognising that a write timestamp alone drives the whole policy.
- **Inlining `System.nanoTime()`.** Makes the boundary untestable and forces sleep-based tests. Inject the clock.
- **Getting the boundary fuzzy.** Using `>` in one place and `>=` in another, or not deciding what happens *at* exactly `ttlNanos`, produces off-by-one flakiness. Pick inclusive-expiry and apply it uniformly.
- **Unconditional `remove(key)` on expiry.** The trap that bites under concurrency: a stale reader race-deletes a fresh concurrent write. `remove(key, entry)` is the fix, and naming *why* it's needed is the strong-answer moment.
- **Forgetting `size()` must purge too.** Counting expired entries as live (or skipping the lazy purge in `size`/`containsKey`) breaks the "live entries only" contract.
- **Assuming lazy expiry is free.** A strong candidate volunteers the memory-leak-of-cold-keys downside and names the active-reaper alternatives before being asked.

## Extensions
- **Active reaper** — a `ScheduledExecutorService` or a `DelayQueue` of keys ordered by deadline, drained periodically, so memory is reclaimed even for keys nobody ever reads again.
- **Per-entry TTL** — accept an optional TTL override on `put` instead of one fixed `ttlNanos` for the whole cache, for symbols with different quote-freshness SLAs.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/quotecache/`)
- Java Interview Primer: Q241 (ConcurrentHashMap atomic primitives — computeIfAbsent/remove(k,v)), Q48 (injected clocks for deterministic time-based tests)
