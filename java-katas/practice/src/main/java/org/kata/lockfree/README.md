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

## The real challenge
- **CAS-loop skeleton**: every mutating operation follows the same three steps — (1) volatile-read a snapshot, (2) compute the proposed next state (pure, no side-effects), (3) `compareAndSet` and return on success or retry on failure. Getting step (2) right — building a new node from a snapshot before the CAS — is the key insight.
- **Michael-Scott two-CAS enqueue + helping**: enqueue requires one CAS to link the new node onto `tail.next` (the linearization point) and a second CAS to swing `tail` forward. Between these two CASes the structure is in an intermediate state. Any thread that observes `tail.next != null` must help advance `tail` before doing its own work — this is what makes the algorithm lock-free rather than merely obstruction-free.
- **Dummy node invariant**: the queue always has a sentinel node whose item is ignored. `head` points to the dummy; the true first element is `head.next`. Dequeue makes `head.next` the new dummy. This eliminates the special case when the queue has exactly one real element.
- **Consistency snapshot in Michael-Scott**: after reading `curTail` and `tailNext` separately, re-read `tail` to detect if another CAS fired between the two reads and restart with a fresh snapshot if it did.
- **ABA with `AtomicStampedStack`**: use `head.get(stampHolder)` to read reference and stamp atomically. Each successful push or pop uses `oldStamp + 1` as `newStamp`. The four-argument `compareAndSet(expectedRef, newRef, expectedStamp, newStamp)` fails if either the reference or the stamp has changed — a recycled node with the same identity but a different generation is correctly rejected.
- **Immutable nodes**: `Node.next` (in Treiber and AtomicStamped) and `Node.item` must be `final`. Immutability is not style — it is what makes reading `node.next` safe without a lock after the node is installed via CAS.

## Run
```
mvn -pl practice test -Dtest=TreiberStackTest,MichaelScottQueueTest,AtomicStampedStackTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/lockfree/`
- Java Interview Primer: Q256 (Treiber stack), Q257 (Michael-Scott queue), Q261 (ABA / AtomicStampedReference), Q49 (happens-before)
