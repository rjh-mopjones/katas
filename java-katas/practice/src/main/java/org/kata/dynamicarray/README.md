# Dynamic Array

> Build the growable array underneath every language's `ArrayList` / `Vec` / `List` — the data structure interviewers use to test whether you actually understand amortized cost, not just how to call one.

## The problem
Implement an index-based, growable array. Unlike a fixed-size array, callers can append past the
initial capacity and the array must grow transparently — and unlike a linked list, reading by
index must stay O(1).

## Requirements
- Construction supports a default starting capacity, and separately a caller-chosen starting
  capacity (negative capacities must be rejected).
- Appending adds an element at the end; inserting at a given index shifts everything from that
  index onward one slot to the right.
- Reading by index returns the element there; replacing by index swaps in a new element and
  returns the old one; removing by index deletes the element and returns it, shifting the tail
  left to close the gap.
- The current size and whether the array is empty must both be queryable.
- Reading, replacing, and removing must reject an index outside `[0, size)`; inserting must reject
  an index outside `[0, size]` (an index equal to the size is a valid append point).

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/dynamicarray/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
