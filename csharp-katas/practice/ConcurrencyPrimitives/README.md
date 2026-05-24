# ConcurrencyPrimitives

Implement three concurrency building blocks: a lock-free atomic counter, a bounded async resource pool, and a read-optimised cache.

## The problem

Real concurrent systems need primitives that go beyond a simple `lock`. This kata teaches you when to use `Interlocked` for lock-free counting, `SemaphoreSlim` for async back-pressure, and `ReaderWriterLockSlim` for read-heavy data structures.

## Requirements

### AtomicCounter
- `Value` — thread-safe read via `Interlocked.Read`.
- `Increment()` — atomic increment, returns new value.
- `Add(long delta)` — atomic add, returns new value.
- `TryIncrementIfBelow(long max)` — CAS retry loop: increment only if current value < max; return `true` if incremented.

### ResourcePool\<T\>
- `ResourcePool(Func<T> factory, int maxSize)` — bounded pool; `factory` creates new instances on demand.
- `AcquireAsync(CancellationToken)` — waits asynchronously when all `maxSize` instances are borrowed.
- `Release(T item)` — returns item to the idle queue and releases a permit.
- `Available` — current count of immediately acquirable items.

### ReadOptimizedCache\<TKey, TValue\>
- `GetOrAdd(TKey key, Func<TKey, TValue> factory)` — factory called at most once per key under a write lock.
- `TryGet(TKey key, out TValue value)` — read under a shared read lock.
- `Count` — current entry count, read under a read lock.

## What you implement

```csharp
public sealed class AtomicCounter
{
    public long Value { get; }
    public long Increment();
    public long Add(long delta);
    public bool TryIncrementIfBelow(long max);
}

public sealed class ResourcePool<T> where T : notnull
{
    public ResourcePool(Func<T> factory, int maxSize);
    public Task<T> AcquireAsync(CancellationToken ct = default);
    public void Release(T item);
    public int Available { get; }
}

public sealed class ReadOptimizedCache<TKey, TValue> where TKey : notnull
{
    public TValue GetOrAdd(TKey key, Func<TKey, TValue> factory);
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value);
    public int Count { get; }
}
```

## The real challenge

- **AtomicCounter**: understand why `Interlocked.Read` is needed even for 64-bit values on 32-bit runtimes; understand the CAS retry loop and the ABA problem.
- **ResourcePool**: the `SemaphoreSlim` models free permits; `ConcurrentQueue<T>` allows lock-free recycling; lazy creation bounds allocations to `maxSize`.
- **ReadOptimizedCache**: use an upgradeable-read lock to avoid a double-check race on insert; understand when `ReaderWriterLockSlim` is better than `lock` — and when it isn't.

## Run

Write your own tests under `practice.tests/ConcurrencyPrimitives/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~ConcurrencyPrimitives"
```

## Reference

- Solution: `solution/ConcurrencyPrimitives/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.threading.interlocked
