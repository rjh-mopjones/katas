# Lock-Free Data Structures

> Implement Treiber's stack, the Michael-Scott queue, and an ABA-safe stamped stack — the three canonical lock-free algorithms in Java concurrency interviews.

## The problem
Build three lock-free data structures without using `synchronized`, `ReentrantLock`, or any blocking primitive. All thread-safety must come from CAS (compare-and-set) operations. Each structure must remain correct under arbitrary concurrent pushes, pops, enqueues, and dequeues — including when threads are preempted mid-operation.

## Requirements

**A lock-free stack (`TreiberStack`)**
- Pushing an item adds it to the top; a null item is rejected.
- Popping removes and returns the top item, or indicates emptiness if there is none.
- Checking emptiness reports whether the stack currently has no elements.

**A lock-free queue (`MichaelScottQueue`)**
- Enqueuing an item appends it to the tail; a null item is rejected.
- Dequeuing removes and returns the head item, or indicates emptiness if there is none.
- Checking emptiness reports whether the queue currently has no real elements.
- Must use a dummy (sentinel) node internally and implement cooperative tail-advancing (helping) so a lagging tail pointer never blocks another thread's progress.

**An ABA-safe stamped stack (`AtomicStampedStack`)**
- Same behaviour as the lock-free stack above.
- Every successful push or pop must increment a monotonic stamp so that ABA is detectable — a stale CAS whose reference matches but whose stamp does not must fail and retry.

## What you're given
Nothing but the problem — you design the whole API and implementation, for all three structures, from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/lockfree/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
