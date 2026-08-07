# Idempotent Processor

## Approach
- The single atomic primitive doing all the work is `ConcurrentHashMap.computeIfAbsent`. It
  atomically checks whether a key is present and, if not, evaluates the mapping function and
  inserts the result — as one operation from the map's perspective. Even when many threads call
  `process` with the same key simultaneously, the mapping function runs *at most once*; competing
  threads block briefly on the internal bin lock and then read the cached result.
- The cache is typed `ConcurrentHashMap<String, Object>` because a single processor instance is
  used across calls with different `T`. The cast back to `T` on return is safe because the caller
  controls the `Supplier<T>` and the generic type is fixed for the duration of a single call.
- The mapping lambda explicitly rejects a `null` result from `action.get()` with an
  `IllegalStateException`. This isn't defensive paranoia — `ConcurrentHashMap` cannot store `null`
  values, so a null result would make the key look absent again on the very next lookup, silently
  breaking the exactly-once guarantee.
- `isProcessed` is a plain `containsKey` — a point-in-time snapshot, not a linearizable
  check-then-act primitive. It exists for monitoring, not for gating logic (`process` already does
  the atomic gating).
- The guarantee is scoped to a single JVM. Distributed exactly-once needs an external, durable
  store (Redis, a DB unique index) whose write completes *before* the message is acknowledged to
  the broker.

## The real challenge
- **`computeIfAbsent` is the only correct primitive**: the naive approach — `if (cache.containsKey(key)) return cache.get(key); else cache.put(key, action.get())` — has a TOCTOU (time-of-check/time-of-use) race: two threads can both pass the `containsKey` check before either executes the action, causing it to run twice. `ConcurrentHashMap.computeIfAbsent` atomically checks and conditionally inserts, holding an internal bin lock so the mapping function runs at most once per absent key.
- **Null result caveat**: `ConcurrentHashMap` does not permit null values. If `action.get()` returns null, the map behaves as though the key is absent on the next lookup and the action will run again — the opposite of idempotency. The implementation must guard against this and throw clearly.
- **Scope of the guarantee**: this is an in-process guarantee only. Distributed exactly-once requires an external shared store (Redis `SET NX EX`, a DB unique index) and the write must be durable before acknowledging the message to the broker.

## Common mistakes & senior signal
- Reaching for `containsKey` + `put` (or `get` + null-check + `put`) instead of `computeIfAbsent` —
  the TOCTOU race is the whole point of the kata; catching it in your own design (rather than
  after a hint) is the signal.
- Forgetting the null-result guard — a `Supplier` that returns `null` looks correct in a
  single-threaded smoke test but silently re-runs on every subsequent call.
- Treating `isProcessed` as safe to gate a decision on ("if not processed, then process") — that
  reintroduces the exact TOCTOU race `computeIfAbsent` was chosen to avoid.
- Not volunteering the JVM-local scope of the guarantee — a strong candidate proactively raises
  "what happens across a restart or multiple instances?" and names Redis `SET NX EX` or a DB
  unique-constraint upsert as the distributed answer.
- Ignoring aliasing: if the cached result is a mutable object and a caller mutates it, every future
  caller for that key sees the mutation. Preferring immutable result types (records) sidesteps this.

## Extensions
- Caffeine / Guava `CacheBuilder` — a bounded LRU cache with TTL, still purely in-process.
- Redis with `SET NX EX` — distributed, survives restarts, TTL is native.
- A relational table with a unique index on the key plus a scheduled cleanup job.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/idempotency/`)
- Java Interview Primer: Q116 (dedupe message processing), Q305 (stop double payment), Q241 (computeIfAbsent atomic)
