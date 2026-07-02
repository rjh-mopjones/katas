# Top of Book

> A single-writer, many-reader quote publisher: the market-data thread publishes the best bid/ask; every strategy thread reads the latest consistent quote without ever blocking the writer.

## The problem

One market-data thread continuously publishes the best bid/ask for a symbol — a multi-word `Quote`
(`bid_px`, `bid_qty`, `ask_px`, `ask_qty`, `seq`). Dozens of strategy threads read the *latest* quote
on every tick of their own loops. Reads are the hot path: they must not block the writer, must not
block each other, and must always return a **consistent** quote — all fields from one single publish,
never a new bid stitched to a stale ask.

## Requirements

- `publish(q)` installs a new quote. There is exactly **one** writer thread.
- `read()` returns the latest quote and may be called from **any number** of reader threads
  concurrently. It must never return a torn quote (fields from two different publishes).
- Readers must not block the writer and must not take a lock or mutate shared state on the read path.
- On an idle structure (no publish in flight) `read()` returns immediately.
- `sequence()` exposes the current sequence counter (for diagnostics/tests).

## What you implement

The public API of `TopOfBook`:

- `void publish(const Quote& q) noexcept`
- `Quote read() const noexcept`
- `std::uint64_t sequence() const noexcept`

`Quote` is provided verbatim. You design the synchronisation.

## The real challenge

- **Seqlock protocol.** The writer bumps a sequence counter to **odd** before touching the payload
  and back to **even** after. A reader snapshots the counter, copies the payload, then re-reads the
  counter — if it changed or was odd, a write overlapped, so retry. This gives wait-free reads that
  never block the single writer.
- **Memory ordering.** Use release ordering (a fence) so the payload writes are ordered *after* the
  odd transition and *before* the even one; use acquire ordering on the reader so seeing the even
  counter also means seeing the payload. Get this wrong and the sequence check passes while the
  reader still sees stale bytes.
- **The payload must be atomic, not plain.** A textbook seqlock copies the payload with plain
  non-atomic loads/stores — under the C++ memory model that is a **data race** (undefined behaviour)
  and ThreadSanitizer flags it. Store each field as a `std::atomic` accessed `relaxed`: same codegen
  on x86/ARM, but race-free by definition. The sequence counter still supplies the *group*
  consistency that relaxed per-field atomics alone do not.
- **Single writer only.** Two writers would corrupt the odd/even protocol. If you had many, you'd
  serialise writers with a mutex (readers stay lock-free).
- **Money angle.** A torn quote — new `bid_px` with stale `ask_px` — can make a strategy cross its own
  book or act on a price that never existed. Real fills, real P&L.

## Run

There are no tests here — writing them is part of the exercise. Add your own
`top_of_book_test.cpp` (and a gated multi-reader stress test) in this directory using
`../../solution/common/harness.hpp` — the harness ships a `kata::StartGate` to release worker threads
together. Verify correctness under ThreadSanitizer, the `-race` analogue:

```
cd cpp-katas
cmake -B build-tsan -DKATAS_SANITIZE=thread && cmake --build build-tsan
ctest --test-dir build-tsan -R top_of_book
```

(If TSan won't run on your machine, a high-iteration stress test with a self-consistency invariant —
every published field derived from one counter — still catches torn reads.)

## Reference

Worked solution: `cpp-katas/solution/top_of_book/`.

Extension: implement a **double-buffer** publisher (two `Quote` slots + an atomic active index the
writer flips) that gives wait-free reads with **no reader retries**, and benchmark it against the
seqlock under a write-heavy load.

Background: Boehm & Adve, *Can Seqlocks Get Along With Programming Language Memory Models?* and
[cppreference — `std::memory_order`](https://en.cppreference.com/w/cpp/atomic/memory_order).
