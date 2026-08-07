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

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/heap/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
