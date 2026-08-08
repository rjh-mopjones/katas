# Binary Heap

> Build the array-backed priority queue behind `java.util.PriorityQueue` — the structure under Dijkstra's shortest path, task schedulers, and streaming top-k.

## The problem
Implement a binary min-heap: a priority queue that keeps the smallest element (or, under a custom comparator, whichever element is "least" by that ordering) always at the root, retrievable in O(1) and removable in O(log n).

## Requirements
- Support construction with natural ordering (elements must be `Comparable`) and construction with a caller-supplied comparator.
- Inserting an element restores the heap invariant.
- Reading the least element without removing it yields that element, or nothing when the heap is empty.
- Removing the least element returns it, or nothing when the heap is empty.
- Expose the current element count and an emptiness check.
- Preserve the complexity guarantees: O(1) to read the least element, O(log n) to insert or remove.
- Optional: a bulk constructor that builds the heap from an existing collection in O(n) (heapify) rather than n sequential inserts.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/heap/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
