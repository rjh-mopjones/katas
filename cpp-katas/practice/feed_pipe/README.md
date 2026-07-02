# Feed Pipe

> A wait-free single-producer/single-consumer ring: the feed-handler thread hands parsed market-data events to the strategy thread — no locks, no allocation, nothing lost.

## The problem

A market-data feed handler runs a tight parse loop on one thread: read datagrams, decode them into
`FeedEvent`s, and hand each event to the strategy thread. The strategy thread consumes events and
updates its books. This is a hand-off across exactly **two** threads — one producer, one consumer —
on the latency-critical path. It must not take a lock, must not allocate per event, and must never
lose, duplicate, or reorder an event.

## Requirements

- `try_push(event)` (producer side) enqueues an event, or returns `false` immediately if the pipe is
  full — it never blocks.
- `try_pop()` (consumer side) returns the next event, or `std::nullopt` immediately if empty — it
  never blocks.
- Exactly **one** thread calls `try_push` and exactly **one** (other) thread calls `try_pop`; the two
  may run fully concurrently.
- Events come out in the exact order they went in (FIFO): none lost, none duplicated, none reordered.
- `capacity()` returns the number of elements the pipe can hold.

## What you implement

The public API of `FeedPipe<T>`:

- `explicit FeedPipe(std::size_t capacity)`
- `bool try_push(const T&)` / `bool try_push(T&&)`
- `std::optional<T> try_pop()`
- `std::size_t capacity() const noexcept`

`FeedEvent` is provided verbatim. You design the ring storage and the atomics.

## The real challenge

- **SPSC needs no CAS.** The producer owns the write index (tail), the consumer owns the read index
  (head). Each index has a single writer, so a plain atomic load/store with the right ordering
  suffices — no compare-and-swap, no spinning. That is what makes both sides wait-free.
- **Release/acquire pairs the hand-off.** The producer writes the payload into slot `tail`, *then*
  release-stores the new tail; the consumer acquire-loads tail before reading the slot. Symmetrically
  for head. Those edges are what let the slots hold **plain** `T` (not atomic) while staying race-free
  — at any instant a given slot has a single accessor. (This is the opposite of the seqlock kata,
  where the payload genuinely must be atomic.)
- **One empty slot distinguishes full from empty.** Store `capacity` elements in `capacity + 1` slots
  so `tail + 1 == head` means full and `head == tail` means empty — no shared size counter that both
  threads would contend on.
- **False sharing kills throughput.** Put `head_` and `tail_` on separate cache lines (`alignas`), or
  the two threads writing adjacent atomics ping-pong the cache line between cores.
- **Money angle.** A dropped event is a missed fill; a duplicated event is a phantom position.

## Run

There are no tests here — writing them is part of the exercise. Add your own `feed_pipe_test.cpp`
(and a gated producer/consumer stress test) in this directory using
`../../solution/common/harness.hpp` — the harness ships a `kata::StartGate` to release the two threads
together. Verify under ThreadSanitizer, the `-race` analogue:

```
cd cpp-katas
cmake -B build-tsan -DKATAS_SANITIZE=thread && cmake --build build-tsan
ctest --test-dir build-tsan -R feed_pipe
```

(If TSan won't run on your machine, a long producer/consumer run asserting strictly increasing `seq`
on the consumer still catches lost/duplicated/reordered events.)

## Reference

Worked solution: `cpp-katas/solution/feed_pipe/`.

Extension: generalise to a bounded **MPMC** queue (Vyukov's algorithm — a per-slot sequence number
and a CAS on the enqueue/dequeue index) and measure how much the single-producer/single-consumer
specialisation bought you versus a `std::mutex` + `std::queue`.

Background: [cppreference — `std::memory_order`](https://en.cppreference.com/w/cpp/atomic/memory_order).
