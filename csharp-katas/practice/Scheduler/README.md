# Scheduler

Implement a delay scheduler that fires one-shot callbacks after a specified delay, using a single background timer.

## The problem

Naive implementations create one OS timer per scheduled item. Under high throughput this wastes OS resources. The challenge is to schedule thousands of callbacks efficiently using a single re-arming timer.

## Requirements

- `Schedule(Action callback, TimeSpan delay)` enqueues the callback and returns a cancellable handle (`IDisposable`).
- Disposing the handle before the callback fires must cancel it (callback is never invoked).
- Only one OS timer is active at any time; it is re-armed to the next due item after each firing.
- Thread-safe: multiple threads may call `Schedule` and `Dispose` concurrently.
- `Dispose()` on the scheduler disarms the timer and prevents further firings.
- Callbacks are invoked outside any lock to prevent re-entrancy deadlocks.
- Accepts a `TimeProvider` so tests can use `FakeTimeProvider.Advance` without real sleeps.

## What you implement

```csharp
public sealed class DelayScheduler : IDisposable
{
    public DelayScheduler(TimeProvider? timeProvider = null);
    public IDisposable Schedule(Action callback, TimeSpan delay);
    public void Dispose();
}
```

## The real challenge

- Use a `PriorityQueue<TElement, TPriority>` keyed by due timestamp for O(log n) enqueue and O(1) peek.
- Single timer: arm to the head of the queue; re-arm after each batch of due callbacks.
- Cancellation via a `HashSet<long>` of cancelled IDs — O(1) cancel, O(1) skip on fire.
- `TimeProvider.CreateTimer` creates the `ITimer` so `FakeTimeProvider.Advance` fires it synchronously.

## Run

Write your own tests under `practice.tests/Scheduler/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Scheduler"
```

## Reference

- Solution: `solution/Scheduler/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.timeprovider.createtimer
