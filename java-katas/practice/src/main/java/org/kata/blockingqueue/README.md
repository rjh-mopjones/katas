# Bounded Blocking Queue

> Implement a thread-safe bounded queue from scratch — the classic Java concurrency interview data structure.

## The problem
Build a generic, fixed-capacity blocking queue. Adding an element must block while the queue is full; removing an element must block while the queue is empty. When space or an element becomes available, the appropriate waiting threads must be woken and allowed to proceed.

## Requirements
- Adding an element blocks until space is available; null elements are rejected.
- Removing an element blocks until an element is available.
- Interruption while waiting is propagated to the caller on both operations.
- Both operations must be safe under concurrent access by multiple producers and consumers simultaneously.
- The current element count and the fixed capacity must both be queryable.
- All waiting must correctly handle spurious wakeups.
- Any references to removed elements must be cleared (GC hygiene).

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/blockingqueue/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
