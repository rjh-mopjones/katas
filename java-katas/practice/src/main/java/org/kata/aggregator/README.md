# Scatter-Gather Aggregator

> Fan out N tasks concurrently and aggregate results — the async pattern at the heart of federated search and microservice fan-out.

## The problem
Build a `ScatterGather` class that accepts an injected `Executor` and a list of `Supplier<T>` tasks. It must submit all tasks concurrently, then provide two gather strategies: one that waits for every task and fails fast on any error, and one that enforces a per-task timeout and returns only the results that arrived in time, silently dropping slow or failed tasks.

## Requirements
- Waiting for every task to complete yields the results in the same order as the input list, or completes exceptionally if any task throws.
- Enforcing a per-task timeout yields whatever tasks finished within the timeout; timed-out or failed tasks are silently omitted, never thrown.
- Tasks must be submitted to the injected executor (not run on the calling thread).
- Constructing the aggregator without an explicit executor falls back to a virtual-thread-per-task executor.
- Null task lists are rejected; null, zero, or negative timeouts are also rejected.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/aggregator/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
