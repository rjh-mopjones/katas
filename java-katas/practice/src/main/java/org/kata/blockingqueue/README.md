# Bounded Blocking Queue

> Implement a thread-safe bounded queue from scratch — the classic Java concurrency interview data structure.

## The problem
Build a generic, fixed-capacity blocking queue backed by a circular array. Producers calling `put()` must block when the queue is full; consumers calling `take()` must block when the queue is empty. When space or an element becomes available, the appropriate waiting threads must be woken and allowed to proceed.

## Requirements
- `put(E element)` blocks until space is available; throws `NullPointerException` for null elements and `InterruptedException` if interrupted while waiting.
- `take()` blocks until an element is available; throws `InterruptedException` if interrupted while waiting.
- Both operations must be safe under concurrent access by multiple producers and consumers simultaneously.
- `size()` returns the current element count; `capacity()` returns the fixed maximum.
- All waiting must use `while`-loop guards, not `if`, to handle spurious wakeups correctly.
- Null elements are rejected; a null slot in the buffer must be cleared after take (GC hygiene).

## What you implement
Implement `BoundedBlockingQueue<E>` from scratch — the public API is the constructor `BoundedBlockingQueue(int capacity)`, `put(E)`, `take()`, `size()`, and `capacity()`. You design the backing data structure, locking strategy, and blocking/waking logic yourself.

## The real challenge
- **One lock, two conditions**: you must use `notFull` and `notEmpty` as separate conditions derived from the same lock. With a single shared condition you would have to call `signalAll()` (wasting work); two conditions let you call `signal()` on exactly the right waiters — one item added means exactly one consumer can proceed.
- **`while`, not `if`**: `Condition.await()` can return spuriously (no corresponding signal), and another thread may consume the freed slot before the woken thread is scheduled. Only a `while` loop re-checking the predicate is correct.
- **Unlock in `finally`**: any exception inside the critical section must not leave the lock held or the queue becomes permanently unusable.
- **Circular-array mechanics**: head/tail advance modulo capacity; size tracks the count; take must null out the vacated slot.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/blockingqueue/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/blockingqueue/`
- Java Interview Primer: Q41 (wait/sleep), Q39 (synchronized), Q47 (latch/barrier), Q255 (producer/consumer)
