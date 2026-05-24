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
The signatures, fields, constructors, and Javadoc are already in place — fill in the logic for:
- `ScatterGather` — `gatherAll(List<Supplier<T>>)` and `gatherAllWithTimeout(List<Supplier<T>>, Duration)`

## The real challenge
- **`allOf` returns `Void`**: `CompletableFuture.allOf(...)` gives you a barrier that fires when all futures complete, but it holds no results. You must keep a reference to the original individual futures and collect their results after the barrier — a common interview stumbling block.
- **`gatherAllWithTimeout` partial results**: wrap each future with `.orTimeout(...)`, then chain `.handle((v, ex) -> ex == null ? Optional.of(v) : Optional.empty())`. The `handle` absorbs exceptions so `allOf` itself never fails; after the barrier you filter for `Optional.isPresent()`. Using `orTimeout` (exceptional on timeout) rather than `completeOnTimeout` (sentinel value) avoids inventing a meaningful default.
- **Keeping tasks lazy**: accept `Supplier<T>`, not pre-built `CompletableFuture<T>`. Pre-built futures start immediately on the caller's thread; suppliers start on your executor when you call `supplyAsync`.
- **Executor injection**: tests inject a controlled executor to verify parallelism and lifecycle. The production default of virtual threads requires no pool sizing.

## Run
```
mvn -pl practice test -Dtest=ScatterGatherTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/aggregator/`
- Java Interview Primer: Q50 (CompletableFuture), Q168 (CF exception handling), Q243 (scatter-gather)
