# Channels

Build a bounded async queue and a multi-producer/single-consumer pipeline on top of it.

## The problem

`System.Threading.Channels` provides a high-performance, natively async alternative to `BlockingCollection<T>`. You build a thin wrapper `BoundedQueue<T>` around `Channel.CreateBounded`, then use it to implement `Pipeline.SumProducedAsync` — a real producer/consumer demonstration where multiple concurrent writers and one reader work through a bounded buffer with natural backpressure.

## Requirements

- `BoundedQueue<T>(capacity)` — creates a bounded channel with `BoundedChannelFullMode.Wait`; throws `ArgumentOutOfRangeException` if `capacity < 1`.
- `WriteAsync(item, ct)` — returns `ValueTask` (allocation-free on the fast path when the channel has space).
- `ReadAllAsync(ct)` — returns `IAsyncEnumerable<T>` that drains until `Complete()` is called.
- `Complete()` — signals the writer side done; the reader loop terminates after all buffered items are consumed.
- `Pipeline.SumProducedAsync` — starts `producerCount` concurrent producers (each writes 0..itemsPerProducer-1), awaits `Task.WhenAll` on producers, calls `Complete()`, drains with `ReadAllAsync`, returns the `long` sum. Propagate producer exceptions.

## What you implement

```csharp
// BoundedQueue<T>
public BoundedQueue(int capacity)
public ValueTask WriteAsync(T item, CancellationToken ct = default)
public IAsyncEnumerable<T> ReadAllAsync(CancellationToken ct = default)
public void Complete()

// Pipeline
public static Task<long> SumProducedAsync(int producerCount, int itemsPerProducer, int capacity, CancellationToken ct = default)
```

## The real challenge

The completion handoff ordering in `Pipeline` is the tricky part. If you `await Task.WhenAll(producers)` before starting the consumer loop, you deadlock when `capacity` is smaller than the total items produced — producers fill the channel and block, but the consumer hasn't started yet. The correct pattern is to start the consumer loop concurrently with the producers and only call `Complete()` (via `ContinueWith`) after all producers finish. Getting the sum type right (`long` not `int`) and propagating producer exceptions via `await completionSignal` are the secondary gotchas.

## Run

Write your own tests under `practice.tests/Channels/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Channels"
```

## Reference

- Worked solution + tests: `solution/Channels/` and `solution.tests/Channels/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/core/extensions/channels
