# Binary Heap

> Build the array-backed priority queue behind `java.util.PriorityQueue` — the structure under Dijkstra's shortest path, task schedulers, and streaming top-k.

## The problem
Implement a binary min-heap: a complete binary tree stored in a flat array (no node objects, no pointers) that keeps the smallest element (or, under a custom comparator, whichever element is "least" by that ordering) always at the root, retrievable in O(1) and removable in O(log n).

## Requirements
- `BinaryHeap()` — natural ordering; elements must be `Comparable`.
- `BinaryHeap(Comparator<? super E> cmp)` — ordering supplied by the caller.
- `void add(E e)` — insert, restoring the heap invariant.
- `E peek()` — the root element without removing it, or `null` if empty.
- `E poll()` — remove and return the root, or `null` if empty.
- `int size()`, `boolean isEmpty()`.
- Optional: `BinaryHeap(Collection<? extends E> items, Comparator<? super E> cmp)` building the heap in O(n) (heapify) rather than n sequential inserts.

## What you implement
Implement `BinaryHeap<E>` from scratch — the public API above is all you get; you own the backing storage, the sift-up/sift-down mechanics, and how the default (no-arg) constructor derives an ordering from `Comparable` without changing the class's type bound.

## The real challenge
- **Implicit tree in an array**: index `i`'s children live at `2i+1`/`2i+2`, its parent at `(i-1)/2`. Completeness (the last level fills left-to-right, no gaps) is what makes this valid — get that wrong and the parent/child math silently points at garbage or the wrong node.
- **Sift-up vs sift-down**: `add` appends at the last slot and bubbles it toward the root while it beats its parent; `poll` moves the last element into the vacated root and bubbles it toward the leaves while a child beats it. Both only ever touch one root-to-leaf path — that is the whole O(log n) argument.
- **Generic array creation**: `new E[capacity]` does not compile (type erasure). Back the heap with `Object[]` and cast on read; document why the cast is safe (only `add`, which is fully generic, ever writes into the array).
- **Natural ordering without a type bound**: the no-arg constructor cannot require `E extends Comparable<E>` without also breaking usability for a comparator-supplied `E` that isn't `Comparable` at all. Work out how `PriorityQueue` itself resolves this — and what happens (and when) if you call the no-arg constructor with a non-`Comparable` type.
- **(Optional) heapify in O(n), not O(n log n)**: naively calling `add` n times is O(n log n). Building the array first and sifting down from the last non-leaf node backward to the root is O(n) — work out why bottom-up beats top-down here.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/heap/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/heap/`
- Java Interview Primer: Q155 (PriorityQueue / heap)
