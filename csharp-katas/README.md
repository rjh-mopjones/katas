# C# Katas

C#-mastery katas: the curriculum is built around the language's distinctive features (LINQ &
iterators, async streams, channels, records, pattern matching, `Span<T>`, generics/variance,
events, expression trees) plus a few classic LLD problems re-done the idiomatic C# way.

Two-sided model (same as the other languages here):

- **`solution/`** + **`solution.tests/`** — full reference implementations and a green test suite
  (the answer key).
- **`practice/`** — the same public types as **blank skeletons** (`throw new NotImplementedException()`)
  with a per-kata `README.md`. **No tests** — you write your own in `practice.tests/`.

## Build & run

Requires the **.NET 8 SDK** (`dotnet --version` ≥ 8.0).

```bash
cd csharp-katas
dotnet test solution.tests                                   # reference suite — green (the answer key)
dotnet test practice.tests                                   # the tests YOU write
dotnet test practice.tests --filter "FullyQualifiedName~Cache"   # one kata
dotnet build                                                 # whole solution
```

## Katas

| Kata | Drills | Difficulty |
|------|--------|-----------|
| `CustomLinq` | extension methods, `yield`, deferred execution | med |
| `Iterators` | hand-written `IEnumerator<T>`, lazy/infinite sequences | med |
| `AsyncStreams` | `IAsyncEnumerable<T>`, `await foreach`, cancellation | hard |
| `Retry` | async/await, `CancellationToken`, `TimeProvider`, backoff+jitter | med |
| `ScatterGather` | `Task.WhenAll`/`WhenAny`, `WaitAsync` timeout, cancellation | hard |
| `Channels` | `System.Threading.Channels`, bounded producer/consumer | hard |
| `RateLimit` | `Interlocked`, `TimeProvider`, token bucket + sliding window | hard |
| `CircuitBreaker` | state machine, events, `TimeProvider` | hard |
| `Scheduler` | `PriorityQueue<,>`, `TimeProvider` timers | hard |
| `Idempotency` | `ConcurrentDictionary.GetOrAdd`, `Lazy<Task<T>>` | med |
| `ConcurrencyPrimitives` | `Interlocked`, `SemaphoreSlim`, `ReaderWriterLockSlim` | med |
| `LockFreeStack` | `Interlocked.CompareExchange` CAS loop, ABA | hard |
| `Cache` | O(1) LRU/LFU (`Dictionary`+`LinkedList`), concurrent LRU | hard |
| `Records` | positional records, `init`, `with`, value equality | easy |
| `ValueEquality` | `IEquatable<T>`/`IComparable<T>`, operator overloading, struct | med |
| `PatternMatching` | switch expressions, property/positional/list patterns | med |
| `Spans` | `ReadOnlySpan<char>`, `stackalloc`, allocation-free parsing | hard |
| `Structs` | `readonly struct`, `in` params, `ref struct` | med |
| `Generics` | `Option`/`Result`, constraints, variance (`in`/`out`) | med |
| `EventsDelegates` | `event`/`EventHandler<T>`, `Func`/`Action`, pub/sub | med |
| `Disposables` | `IDisposable` + `IAsyncDisposable`, `using`/`await using` | med |
| `Nullable` | nullable reference types, flow analysis, `[NotNullWhen]` | med |
| `ExpressionTrees` | `System.Linq.Expressions`, predicate building, visitors | hard |

See [`practice/README.md`](practice/README.md) for the practice workflow.
