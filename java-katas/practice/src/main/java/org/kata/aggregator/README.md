# Scatter-Gather Aggregator

> Fan out N tasks concurrently and aggregate results — the async pattern at the heart of federated search and microservice fan-out.

## The problem
Build a `ScatterGather` class that accepts an injected `Executor` and a list of `Supplier<T>` tasks. It must submit all tasks concurrently, then provide two gather strategies: one that waits for every task and fails fast on any error, and one that enforces a per-task timeout and returns only the results that arrived in time, silently dropping slow or failed tasks.

## Requirements
- `gatherAll(List<Supplier<T>> tasks)` returns a `CompletableFuture<List<T>>` that completes with results in the same order as the input list, or completes exceptionally if any task throws.
- `gatherAllWithTimeout(List<Supplier<T>> tasks, Duration timeout)` returns a `CompletableFuture<List<T>>` that completes with whatever tasks finished within the timeout; timed-out or failed tasks are silently omitted (never throw).
- Tasks must be submitted to the injected executor (not run on the calling thread).
- The default no-arg constructor uses a virtual-thread-per-task executor.
- Both methods reject null `tasks` arguments; `gatherAllWithTimeout` also rejects null, zero, or negative timeouts.

## What you implement
Implement `ScatterGather` from scratch — the public API is a no-arg constructor, a constructor accepting an `Executor`, `gatherAll(List<Supplier<T>>)`, and `gatherAllWithTimeout(List<Supplier<T>>, Duration)`. You design the scatter, barrier, and gather logic yourself.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/aggregator/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
