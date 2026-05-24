# Idempotent Processor

> Guarantee exactly-once execution per idempotency key even when duplicate messages arrive concurrently — the core of safe payment and Kafka consumer dedup.

## The problem
Implement a processor that wraps arbitrary `Supplier<T>` actions so that each unique idempotency key triggers the action exactly once, no matter how many times `process()` is called with that key. Concurrent duplicate deliveries of the same key must race safely: exactly one thread executes the action, all others return the cached result.

## Requirements
- `process(String idempotencyKey, Supplier<T> action)` executes `action.get()` on the first call for a key, caches the result, and returns it. On all subsequent calls for the same key it returns the cached result without invoking `action`.
- Under concurrent duplicate delivery (multiple threads calling `process` with the same key simultaneously), the action runs exactly once per key.
- `action` must not return `null` — `ConcurrentHashMap` cannot store null values, and null would be misinterpreted as an absent key, causing the action to re-run.
- `isProcessed(String key)` returns `true` if the key has already been processed (point-in-time snapshot).
- Null `idempotencyKey` or `action` arguments are rejected.

## What you implement
Implement `IdempotentProcessor` from scratch — the public API is `process(String idempotencyKey, Supplier<T> action)` and `isProcessed(String idempotencyKey)`. You design the cache structure and the atomic exactly-once guarantee yourself.

## The real challenge
- **`computeIfAbsent` is the only correct primitive**: the naive approach — `if (cache.containsKey(key)) return cache.get(key); else cache.put(key, action.get())` — has a TOCTOU (time-of-check/time-of-use) race: two threads can both pass the `containsKey` check before either executes the action, causing it to run twice. `ConcurrentHashMap.computeIfAbsent` atomically checks and conditionally inserts, holding an internal bin lock so the mapping function runs at most once per absent key.
- **Null result caveat**: `ConcurrentHashMap` does not permit null values. If `action.get()` returns null, the map behaves as though the key is absent on the next lookup and the action will run again — the opposite of idempotency. The implementation must guard against this and throw clearly.
- **Scope of the guarantee**: this is an in-process guarantee only. Distributed exactly-once requires an external shared store (Redis `SET NX EX`, a DB unique index) and the write must be durable before acknowledging the message to the broker.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/idempotency/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/idempotency/`
- Java Interview Primer: Q116 (dedupe message processing), Q305 (stop double payment), Q241 (computeIfAbsent atomic)
