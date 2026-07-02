# Tick Buffer

> A fixed-capacity rolling window of the last N market ticks for a symbol — allocation-free on the hot path.

## The problem

A pricing/analytics loop keeps the last N ticks for a symbol: the raw material for a rolling VWAP, a
last-N-trades feed, a short-horizon momentum signal. It sees millions of ticks a second, so the window
is a *bounded* history that recycles its storage — once it holds N ticks, each new tick overwrites the
oldest. The hard rule on this hot path is **zero heap allocation per tick**: you allocate the backing
store once, at construction, and never again, however many ticks stream through.

The naive backing store — `std::vector<T>` or `new T[capacity]` — is wrong, because both
*default-construct* every element up front (and `vector` may reallocate as it grows). A `Tick` has no
meaningful empty value. What you want is `capacity` slots of raw, uninitialised, aligned memory, with a
live element constructed in a slot only when a tick is recorded there, and destroyed when it is
overwritten or the buffer dies.

## Requirements

- `TickBuffer(capacity)` reserves room for `capacity` elements; `capacity == 0` throws
  `std::invalid_argument`.
- `record(tick)` appends. While `size() < capacity()` it fills a free slot; once full it **overwrites
  the oldest** element.
- `latest()` returns the most-recently recorded tick, or throws `std::out_of_range` if empty.
- `snapshot()` returns a copy of the live window, **oldest -> newest**.
- `size()`, `capacity()`, `empty()`, `full()` report state and are `noexcept`.
- The type is **non-copyable and non-movable** (copy/move are deleted) — keep the kata focused on the
  storage discipline.
- No element is ever leaked (destructor runs for every recorded tick) and none is double-destroyed.

## What you implement

The public API of `TickBuffer<T>`:

- `explicit TickBuffer(std::size_t capacity)`
- `~TickBuffer()`
- copy and move operations are deleted
- `void record(const T& tick)`
- `const T& latest() const`
- `std::vector<T> snapshot() const`
- `std::size_t size() const noexcept`, `std::size_t capacity() const noexcept`,
  `bool empty() const noexcept`, `bool full() const noexcept`

`Tick` is provided verbatim. You design the backing store, the head/count bookkeeping, and the lifetime
management.

## The real challenge

- **Raw, aligned storage — not `vector<T>`.** Allocate the backing store with
  `::operator new(sizeof(T) * capacity, std::align_val_t(alignof(T)))` and free it with the matching
  sized, aligned `::operator delete`. A `vector<T>` or `new T[]` would default-construct every element
  and can reallocate — exactly what you must avoid.
- **Placement-new to construct a live element.** `::new (slot) T(tick)` constructs one T in a raw slot.
  The slot is a bag of bytes until you do this; reading it before is undefined behaviour.
- **Destroy before overwrite.** When the buffer is full, `record` must `std::destroy_at(slot)` the
  element it is about to overwrite *before* placement-newing the replacement — skip it and the
  overwritten element's destructor never runs (a leak, e.g. of a `std::string` field).
- **Ring math.** The write slot is `(head + count) % capacity`, the oldest is `head`; overwriting
  advances `head`. Get the modulo wrong and `snapshot()`/`latest()` read the wrong — or an
  already-destroyed — slot.
- **Exception-safety in the destructor.** Destroy *exactly* the live elements (walk `count` slots from
  `head`, wrapping), then free the raw store. Never destroy a slot that was never constructed.
- **Money angle.** A per-tick allocation stalls the pricing loop under malloc contention and blows the
  latency budget on the ticks that move the market; a leaked element slowly OOMs a long-running
  session; reading a destroyed slot feeds a stale or garbage price into the VWAP and mis-marks the book.

## Run

There are no tests here — writing them is part of the exercise. Add your own `tick_buffer_test.cpp` in
this directory (use `../../solution/common/harness.hpp`), wire it into CMake, then:

```
cd cpp-katas && ctest --test-dir build -R tick_buffer
```

## Reference

Worked solution: `cpp-katas/solution/tick_buffer/`.

Extension: add a rolling-VWAP accessor that maintains running sums incrementally as ticks are recorded
and overwritten (so it stays O(1) per tick, not O(N) per query), and add a forward iterator over the
live window (oldest -> newest). Then make the buffer copyable *and* movable with a correct deep copy of
only the live elements — placement-new each live element from the source, leave the source's spare
slots raw — which is the rule-of-five version of this storage discipline.

Background: [cppreference — std::construct_at / placement new](https://en.cppreference.com/w/cpp/memory/construct_at).
