# Idempotent Processor

> Guarantee exactly-once execution per idempotency key even when duplicate messages arrive concurrently — the core of safe payment and Kafka consumer dedup.

## The problem
Implement a processor that wraps arbitrary actions so that each unique idempotency key triggers the action exactly once, no matter how many times it is invoked with that key. Concurrent duplicate deliveries of the same key must race safely: exactly one thread executes the action, all others return the cached result.

## Requirements
- Processing a key for the first time executes the action, caches the result, and returns it. Processing the same key again returns the cached result without invoking the action again.
- Under concurrent duplicate delivery (multiple threads processing the same key simultaneously), the action runs exactly once per key.
- The wrapped action must not return null — a null result cannot be distinguished from "not yet processed", which would let the action re-run on a later call for the same key.
- Checking whether a key has already been processed returns a point-in-time snapshot: true once that key's action has completed and been cached.
- A null key or a null action is rejected.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/idempotency/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
