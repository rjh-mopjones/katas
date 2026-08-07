# Bounded Blocking Queue

> Implement a thread-safe bounded queue from scratch — the classic Java concurrency interview data structure.

## The problem
Build a generic, fixed-capacity blocking queue. Producers calling `put()` must block when the queue is full; consumers calling `take()` must block when the queue is empty. When space or an element becomes available, the appropriate waiting threads must be woken and allowed to proceed.

## Requirements
- `put(E element)` blocks until space is available; throws `NullPointerException` for null elements and `InterruptedException` if interrupted while waiting.
- `take()` blocks until an element is available; throws `InterruptedException` if interrupted while waiting.
- Both operations must be safe under concurrent access by multiple producers and consumers simultaneously.
- `size()` returns the current element count; `capacity()` returns the fixed maximum.
- All waiting must correctly handle spurious wakeups.
- Null elements are rejected; any references to removed elements must be cleared (GC hygiene).

## What you implement
Implement `BoundedBlockingQueue<E>` from scratch — the public API is the constructor `BoundedBlockingQueue(int capacity)`, `put(E)`, `take()`, `size()`, and `capacity()`. You design the backing data structure, locking strategy, and blocking/waking logic yourself.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/blockingqueue/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
