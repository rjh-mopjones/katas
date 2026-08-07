# Dynamic Array

> Build the growable array underneath every language's `ArrayList` / `Vec` / `List` — the data structure interviewers use to test whether you actually understand amortized cost, not just how to call one.

## The problem
Implement an index-based, growable array. Unlike a fixed-size array, callers can append past the initial capacity and the array must grow transparently — and unlike a linked list, `get(index)` must stay O(1).

## Requirements
- `DynamicArray()` starts at a default capacity; `DynamicArray(int initialCapacity)` starts at a caller-chosen capacity (reject negative capacities).
- `add(E e)` appends; `add(int index, E e)` inserts at `index`, shifting everything from `index` onward one slot right.
- `get(int index)` reads; `set(int index, E e)` replaces and returns the old value; `remove(int index)` removes and returns the removed value, shifting the tail left.
- `size()` and `isEmpty()`.
- `get`/`set`/`remove` throw `IndexOutOfBoundsException` outside `[0, size)`; `add(int, E)` throws outside `[0, size]` (index `== size` is a valid append point).

## What you implement
`DynamicArray<E>` from scratch: both constructors, `add(E)`, `add(int, E)`, `get(int)`, `set(int, E)`, `remove(int)`, `size()`, `isEmpty()`. You design the backing storage, the growth trigger, and the shifting.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/dynamicarray/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
