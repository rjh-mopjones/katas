# SPSC Ring *(unsafe capstone)*

> A wait-free single-producer/single-consumer ring between the feed thread and the strategy thread — no locks, no allocation per item. Written with `unsafe`, verified with Miri.

## The problem

The feed-parser thread hands parsed events to the strategy thread over a fixed-capacity ring. Exactly
**one** producer and **one** consumer, running concurrently. `channel(cap)` returns a `Producer` and a
`Consumer`; each moves to its own thread. Neither `try_push` nor `try_pop` may block, allocate, lose,
duplicate, or reorder an item.

## Requirements

- `try_push(value)` enqueues, or returns `Err(value)` if full; never blocks.
- `try_pop()` returns the next item, or `None` if empty; never blocks.
- FIFO: items come out in the exact order they went in.
- Exactly one producer thread and one consumer thread; the two run concurrently.
- No leak or double-free of items still in the ring when it is dropped.

## What you implement

- `fn channel<T>(capacity: usize) -> (Producer<T>, Consumer<T>)`
- `impl<T> Producer<T> { fn try_push(&self, value: T) -> Result<(), T> }`
- `impl<T> Consumer<T> { fn try_pop(&self) -> Option<T> }`

You design the ring (an `UnsafeCell<MaybeUninit<T>>` buffer + atomic `head`/`tail`), the `Drop`, and
the `Send`/`Sync` story.

## The real challenge

- **`unsafe` is required.** Safe Rust can't express "two threads touch this buffer but never the same
  slot at once". Slots go in `UnsafeCell<MaybeUninit<T>>`; you uphold the invariant by hand: producer
  only writes slot `tail`, consumer only reads slot `head`.
- **Memory ordering.** Producer writes the slot, *then* `Release`-stores `tail`; consumer
  `Acquire`-loads `tail` before reading the slot (and symmetrically for `head`). Those release/acquire
  edges are what make the concurrent slot access sound.
- **Full vs empty.** Keep one slot always empty (`tail + 1 == head` = full) so you don't need a shared
  length counter both threads contend on.
- **`Send`/`Sync` by hand.** `UnsafeCell` is `!Sync`; assert `unsafe impl Send + Sync for Ring` and
  justify it. A senior touch: make `Producer`/`Consumer` `!Sync` (via `PhantomData<Cell<()>>`) so the
  *type system* enforces single-producer/single-consumer — movable, not shareable.
- **`Drop`.** Elements still in `[head, tail)` when the ring drops must each be dropped exactly once.
- **Money angle.** A dropped event is a missed fill; a duplicated one is a phantom position.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests`: FIFO,
full/empty, wrap-around, a `Drop`-counting payload to prove no leak/double-free, and a gated
producer/consumer thread test (`std::thread::scope` + `std::sync::Barrier`) asserting strict FIFO.
Then:

```
cd rust-katas && cargo test -p practice spscring
# and the UB / data-race proof (the -race analogue) — needs: rustup component add miri
cargo +nightly miri test -p practice spscring
```

## Reference

Worked solution: `rust-katas/solution/src/spscring/`. This is the Rust twin of the C++ `feed_pipe`
kata.

Extension: cache-line-pad `head` and `tail` (`#[repr(align(64))]`) to kill false sharing; then
generalise to a bounded MPMC queue (per-slot sequence numbers + CAS, à la Vyukov).

Background: [Rustonomicon — Working with Uninitialized Memory](https://doc.rust-lang.org/nomicon/uninitialized.html)
and [`std::sync::atomic` ordering](https://doc.rust-lang.org/std/sync/atomic/enum.Ordering.html).
