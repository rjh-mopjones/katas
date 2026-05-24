# Scatter/Gather

Fan out async work concurrently and collect results, with optional per-task timeout.

## The problem

Sequential I/O multiplies latency. The scatter/gather pattern starts all independent tasks at once (scatter) and waits for them to complete before returning (gather). You implement two variants: `GatherAllAsync` — all tasks must succeed — and `GatherBeforeTimeoutAsync` — collect whichever tasks finish within a deadline, silently dropping slow or faulted ones.

## Requirements

- `GatherAllAsync` — starts all task factories immediately (materialise to an array first), awaits `Task.WhenAll`, returns results in original input order.
- `GatherBeforeTimeoutAsync` — creates a linked `CancellationTokenSource`, passes its token to each factory, races each started task against `timeout` using `Task.WaitAsync(timeout, timeProvider)`, collects only successful outcomes in input order, then cancels the linked CTS to signal any still-running tasks.
- Both methods preserve input order in the returned list regardless of completion order.
- `GatherAllAsync` propagates all exceptions via `Task.WhenAll`; `GatherBeforeTimeoutAsync` silently drops any task that times out or throws.

## What you implement

```csharp
public static Task<IReadOnlyList<T>> GatherAllAsync<T>(
    IEnumerable<Func<CancellationToken, Task<T>>> tasks,
    CancellationToken ct = default)

public static Task<IReadOnlyList<T>> GatherBeforeTimeoutAsync<T>(
    IEnumerable<Func<CancellationToken, Task<T>>> tasks,
    TimeSpan timeout,
    TimeProvider timeProvider,
    CancellationToken ct = default)
```

## The real challenge

`GatherBeforeTimeoutAsync` has several non-obvious requirements working together. A single `Task.WhenAll` with a global timeout would bail as soon as the first task times out — you need per-task `WaitAsync` so fast tasks succeed even if others are slow. The linked CTS is necessary to avoid resource leaks: without it, "loser" tasks keep running after gathering completes. The order guarantee also trips people up: sorting by original index after gathering is the clean solution.

## Run

Write your own tests under `practice.tests/ScatterGather/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~ScatterGather"
```

## Reference

- Worked solution + tests: `solution/ScatterGather/` and `solution.tests/ScatterGather/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.threading.tasks.task.whenall
