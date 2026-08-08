# Ring Buffer

> Build a bounded, array-backed FIFO queue — the circular buffer behind `ArrayDeque`, log ring buffers, and audio playback buffers.

## The problem
Implement a generic queue with a fixed maximum capacity, backed by a single array. Producers offer elements until the buffer is full; consumers poll elements in the order they arrived. The buffer never grows and never allocates per element after construction — a full offer simply fails.

## Requirements
- Constructing the buffer requires a positive capacity.
- Offering an element when the buffer is full fails and leaves the buffer unchanged.
- Removing the head element returns it and advances past it, or signals empty (rather than throwing) when there's nothing to remove.
- Peeking returns the head element without removing it, or signals empty when there's nothing there.
- Current size, and whether the buffer is empty or full, must all be queryable in O(1).
- Capacity is fixed at construction and must be queryable.
- No per-element allocation after construction — a single fixed-size backing array only.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/ringbuffer/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
