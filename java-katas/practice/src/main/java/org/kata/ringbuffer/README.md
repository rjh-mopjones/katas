# Ring Buffer

> Build a bounded, array-backed FIFO queue — the circular buffer behind `ArrayDeque`, log ring buffers, and audio playback buffers.

## The problem
Implement a generic queue with a fixed maximum capacity, backed by a single array. Producers offer elements until the buffer is full; consumers poll elements in the order they arrived. The buffer never grows and never allocates per element after construction — a full offer simply fails.

## Requirements
- `RingBuffer(int capacity)` — capacity must be positive.
- `offer(E e)` returns `false` (and changes nothing) if the buffer is full.
- `poll()` removes and returns the head element, or `null` if empty.
- `peek()` returns the head element without removing it, or `null` if empty.
- `size()`, `isEmpty()`, `isFull()`, `capacity()` report current state.
- No per-element allocation after construction — a single fixed-size backing array.

## What you implement
Implement `RingBuffer<E>` from scratch — the public API is the constructor plus `offer`, `poll`, `peek`, `size`, `isEmpty`, `isFull`, and `capacity`. You design the internal cursor arithmetic and the full/empty disambiguation yourself.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/ringbuffer/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
