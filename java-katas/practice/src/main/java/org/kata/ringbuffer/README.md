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

## The real challenge
- **Modular wrap-around**: both the head and tail cursors advance mod `capacity`, wrapping back to index `0` when they run off the end of the array — get the wrap-around index math right in both `offer` and `poll`.
- **Full vs. empty ambiguity**: with only a head and tail cursor, `head == tail` means either "completely empty" or "completely full" — you need a third signal (a count, or burning one array slot) to tell them apart.
- **No element loitering**: nulling out a slot after `poll` avoids pinning a polled object's reference from a stale array cell.
- **Generic array creation**: `new E[capacity]` doesn't compile (type erasure) — back the buffer with `Object[]` and cast on read, documenting why the cast is safe.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/ringbuffer/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/ringbuffer/`
- Java Interview Primer: `ArrayDeque` internals / circular buffers
