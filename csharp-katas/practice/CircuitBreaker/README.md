# CircuitBreaker

Implement a classic three-state circuit breaker that protects downstream dependencies by failing fast and probing for recovery.

## The problem

When a downstream service is unavailable, every caller blocks and retries, amplifying load and slowing the system. A circuit breaker detects repeated failures and short-circuits subsequent calls, giving the dependency time to recover.

## Requirements

- **Closed**: forward all calls. On `failureThreshold` consecutive exceptions, trip to **Open**.
- **Open**: throw `CircuitOpenException` immediately without invoking the action. After `openDuration` elapses, transition to **HalfOpen**.
- **HalfOpen**: forward calls as probes. Any failure reopens immediately. After `halfOpenSuccessesToClose` consecutive successes, close the circuit.
- Raise `StateChanged` event after each transition (outside the lock to avoid re-entrancy).
- Accept a `TimeProvider` for deterministic testing.
- Constructors must throw `ArgumentOutOfRangeException` for invalid arguments.

## What you implement

```csharp
public sealed class CircuitBreaker
{
    public event EventHandler<CircuitState>? StateChanged;
    public CircuitState State { get; }

    public CircuitBreaker(int failureThreshold, TimeSpan openDuration, int halfOpenSuccessesToClose, TimeProvider? timeProvider = null);
    public Task<T> ExecuteAsync<T>(Func<CancellationToken, Task<T>> action, CancellationToken ct = default);
}
```

Fixtures (copy verbatim): `CircuitState` enum, `CircuitOpenException`.

## The real challenge

- Do not hold a lock across an `await` — acquire it to check state before, and again to record outcome after.
- `Open → HalfOpen` transition happens lazily on the next call, not via a timer.
- Fire `StateChanged` *outside* the lock to avoid deadlocks with re-entrant subscribers.
- Use `TimeProvider.GetTimestamp` / `GetElapsedTime` to measure the open duration.

## Run

Write your own tests under `practice.tests/CircuitBreaker/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~CircuitBreaker"
```

## Reference

- Solution: `solution/CircuitBreaker/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/standard/threading/overview-of-synchronization-primitives
