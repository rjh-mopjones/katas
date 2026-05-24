# Idempotency

Implement an `IdempotentRunner` that executes an async action at most once per key, even under concurrent duplicate requests.

## The problem

In distributed systems, retries and duplicates are inevitable. Without idempotency, the same logical operation (e.g. charge a credit card, send an email) may execute multiple times. The runner must ensure exactly-once execution per key across concurrent and repeated calls.

## Requirements

- `RunOnceAsync<T>(key, action, ct)` executes `action` exactly once for each unique `key`.
- All concurrent callers with the same key receive the same `Task<T>` — they all await the same result.
- Subsequent calls after completion return the cached result (or cached exception) immediately.
- Exception caching is intentional: a faulted task is stored and re-observed on every subsequent call.
- `ArgumentException` must be thrown when `key` is null or empty.

## What you implement

```csharp
public sealed class IdempotentRunner
{
    public Task<T> RunOnceAsync<T>(string key, Func<CancellationToken, Task<T>> action, CancellationToken ct = default);
}
```

## The real challenge

- TOCTOU race: a naive "check then insert" is not atomic under concurrency.
- `ConcurrentDictionary.GetOrAdd` + `Lazy<Task<T>>`: the `Lazy` wrapper ensures the factory runs at most once even if `GetOrAdd`'s factory is called by multiple threads.
- Storing `Lazy<Task<T>>` as `object` allows a single dictionary for all result types — cast on retrieval.
- Understand why faulted task caching prevents retry storms (and when you might want a different strategy).

## Run

Write your own tests under `practice.tests/Idempotency/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Idempotency"
```

## Reference

- Solution: `solution/Idempotency/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.collections.concurrent.concurrentdictionary-2.getoradd
