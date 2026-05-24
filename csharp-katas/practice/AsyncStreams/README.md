# Async Streams

Implement a pull-based async producer and three LINQ-style async stream operators.

## The problem

`IAsyncEnumerable<T>` lets a producer yield items one at a time across async boundaries — no buffering required, with natural backpressure. You need to write `NumberSource.ProduceAsync`, which yields integers 0 to count-1 with a `Task.Yield()` between each, and three lazy operators: `SelectAsync`, `WhereAsync`, and `TakeAsync`. Each operator must correctly forward the caller's `CancellationToken` into the upstream iteration.

## Requirements

- `NumberSource.ProduceAsync(count, ct)` — yields integers `0..count-1`; checks `ct` before each yield; throws `ArgumentOutOfRangeException` if `count < 0`.
- `SelectAsync<T, TResult>(source, selector, ct)` — projects each element; checks `ct` before and during iteration.
- `WhereAsync<T>(source, predicate, ct)` — filters elements; checks `ct` before and during iteration.
- `TakeAsync<T>(source, count, ct)` — yields at most `count` elements; stops pulling once the limit is reached.
- All operators use `await foreach` with `.WithCancellation(ct)` to forward cancellation upstream.

## What you implement

```csharp
// NumberSource
public IAsyncEnumerable<int> ProduceAsync(int count, CancellationToken ct = default)

// AsyncSequenceOperators
public static IAsyncEnumerable<TResult> SelectAsync<T, TResult>(
    this IAsyncEnumerable<T> source, Func<T, TResult> selector, CancellationToken ct = default)
public static IAsyncEnumerable<T> WhereAsync<T>(
    this IAsyncEnumerable<T> source, Func<T, bool> predicate, CancellationToken ct = default)
public static IAsyncEnumerable<T> TakeAsync<T>(
    this IAsyncEnumerable<T> source, int count, CancellationToken ct = default)
```

## The real challenge

The skeleton deliberately omits `async` from all signatures (an `async IAsyncEnumerable` method that contains no `await` or `yield return` won't compile). You must add `async` yourself and understand that `IAsyncEnumerable<T>` methods are `async` iterator methods — they require both `await` (for async suspension) and `yield return` (to emit values). The second gotcha is `[EnumeratorCancellation]` and `.WithCancellation(ct)`: without them the consumer's cancellation token is silently ignored by the upstream producer.

## Run

Write your own tests under `practice.tests/AsyncStreams/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~AsyncStreams"
```

## Reference

- Worked solution + tests: `solution/AsyncStreams/` and `solution.tests/AsyncStreams/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/asynchronous-programming/generate-consume-asynchronous-stream
