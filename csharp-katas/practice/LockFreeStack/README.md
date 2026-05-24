# LockFreeStack

Implement a lock-free LIFO stack using the Treiber algorithm and `Interlocked.CompareExchange`.

## The problem

A lock-based stack serialises all pushes and pops through a single mutex. Under high concurrency, every thread that wants to push or pop must wait its turn, creating a bottleneck. A lock-free stack allows multiple threads to make forward progress simultaneously — no thread can be blocked by another sleeping thread.

## Requirements

- `Push(T item)` — prepend a new node to the head; must be lock-free.
- `TryPop(out T item)` — remove and return the head; return `false` on empty; must be lock-free.
- `IsEmpty` — returns `true` when the stack has no elements.
- No `lock`, `Monitor`, `Mutex`, or `SemaphoreSlim` allowed — use only `Interlocked` / `Volatile`.

## What you implement

```csharp
public sealed class LockFreeStack<T>
{
    public bool IsEmpty { get; }
    public void Push(T item);
    public bool TryPop([MaybeNullWhen(false)] out T item);
}
```

## The real challenge

- **Treiber stack**: represent the stack as a singly-linked list. `_head` is a single field updated atomically via `Interlocked.CompareExchange`.
- **CAS retry loop**: read `oldHead`, build `newHead`, swap atomically. If another thread raced you, retry from the top.
- **ABA problem**: understand why it is safe in a GC'd runtime (no free list → no address reuse while a reference is held).
- `Volatile.Read` on `_head` ensures a fresh read on each iteration.

## Run

Write your own tests under `practice.tests/LockFreeStack/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~LockFreeStack"
```

## Reference

- Solution: `solution/LockFreeStack/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.threading.interlocked.compareexchange
