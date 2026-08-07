# Lock-Free Data Structures

> Implement Treiber's stack, the Michael-Scott queue, and an ABA-safe stamped stack — the three canonical lock-free algorithms in Java concurrency interviews.

## The problem
Build three lock-free data structures without using `synchronized`, `ReentrantLock`, or any blocking primitive. All thread-safety must come from `AtomicReference` and `AtomicStampedReference` CAS operations. Each structure must remain correct under arbitrary concurrent pushes, pops, enqueues, and dequeues — including when threads are preempted mid-operation.

## Requirements

**`TreiberStack<E>`**
- `push(E item)` adds to the top; rejects null.
- `pop()` removes and returns the top as `Optional<E>`, or `Optional.empty()` if empty.
- `isEmpty()` returns true iff the stack has no elements.

**`MichaelScottQueue<E>`**
- `enqueue(E item)` appends to the tail; rejects null.
- `dequeue()` removes and returns the head as `Optional<E>`, or `Optional.empty()` if empty.
- `isEmpty()` returns true iff the queue has no real elements.
- Must use a dummy (sentinel) node and implement cooperative tail-advancing (helping).

**`AtomicStampedStack<E>`**
- Same API as `TreiberStack`.
- Every successful push or pop must increment the monotonic stamp so that ABA is detectable — a stale CAS whose reference matches but whose stamp does not must fail and retry.

## What you implement
Implement `TreiberStack`, `MichaelScottQueue`, and `AtomicStampedStack` from scratch — the public API is `push`/`pop`/`isEmpty` for the stacks and `enqueue`/`dequeue`/`isEmpty` for the queue (all returning `Optional<E>` on removal). You design the node structure, atomic reference fields, and all CAS loops yourself.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/lockfree/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
