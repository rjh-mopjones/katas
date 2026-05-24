# Cache

Implement three bounded caches — LRU, LFU, and a thread-safe LRU wrapper — each with O(1) get and put.

## The problem

Caches are everywhere, but naive implementations are either slow (O(n) eviction) or unsafe under concurrency. The challenge is achieving O(1) operations by choosing the right data structures, and understanding the trade-offs between eviction policies.

## Requirements

### LruCache\<TKey, TValue\>
- `TryGet` promotes the accessed entry to most-recently-used and returns its value.
- `Put` inserts or updates; evicts the least-recently-used entry when at capacity.
- Not thread-safe (use `ConcurrentLruCache` for thread safety).

### LfuCache\<TKey, TValue\>
- `TryGet` increments the access frequency of the entry.
- `Put` inserts with frequency 1; evicts the entry with the lowest frequency (tie-broken by LRU order within the same frequency).
- Not thread-safe.

### ConcurrentLruCache\<TKey, TValue\>
- Thread-safe wrapper around `LruCache` using a single `lock`.
- All three operations (`TryGet`, `Put`, `Count`) must hold the lock.

All three implement `ICache<TKey, TValue>`. Constructors throw `ArgumentOutOfRangeException` for capacity < 1.

## What you implement

```csharp
public sealed class LruCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    public LruCache(int capacity);
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value);
    public void Put(TKey key, TValue value);
    public int Count { get; }
}

public sealed class LfuCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    public LfuCache(int capacity);
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value);
    public void Put(TKey key, TValue value);
    public int Count { get; }
}

public sealed class ConcurrentLruCache<TKey, TValue> : ICache<TKey, TValue> where TKey : notnull
{
    public ConcurrentLruCache(int capacity);
    public bool TryGet(TKey key, [MaybeNullWhen(false)] out TValue value);
    public void Put(TKey key, TValue value);
    public int Count { get; }
}
```

Fixture (copy verbatim): `ICache<TKey, TValue>`.

## The real challenge

- **LRU O(1)**: `Dictionary<TKey, LinkedListNode<...>>` gives O(1) lookup; `LinkedList<T>` gives O(1) move-to-head and remove-last. Understand why a plain `List<T>` would be O(n).
- **LFU O(1)**: frequency buckets as linked lists + a `minFreq` tracker. When a bucket empties and its frequency equals `minFreq`, increment `minFreq` by exactly 1.
- **ConcurrentLruCache**: understand why `ReaderWriterLockSlim` provides no benefit here — every LRU `TryGet` is a write (it mutates the recency list).

## Run

Write your own tests under `practice.tests/Cache/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Cache"
```

## Reference

- Solution: `solution/Cache/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.collections.generic.linkedlist-1
