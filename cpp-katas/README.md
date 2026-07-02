# C++ Katas

Modern-C++ interview katas, themed as components of a **low-latency trading platform**. Each kata is
a real system component — an exchange session, a quote publisher, a feed pipe — whose *implementation
depth* is a core C++ skill (RAII/move semantics, manual object lifetime, intrusive ref-counting,
seqlocks, lock-free SPSC). Half exercise C++ **mechanics**, half exercise **concurrency correctness**.

> **Write your own tests.** The `practice/` side ships *without* tests on purpose — designing the
> tests (including the gated concurrency stress tests) is part of the exercise. The `solution/` twin
> carries reference tests to compare against afterwards.

## Layout

Two mirrored trees; every kata sits at the same relative path in both.

```
cpp-katas/
├── solution/     full reference implementations + the hand-rolled test suite  (always GREEN)
│   └── common/harness.hpp   ~100-line, stdlib-only test harness (no GoogleTest/Catch2)
└── practice/     same components as blank skeletons; a compile-check target proves they build; NO tests
```

The test framework is a hand-rolled header (`solution/common/harness.hpp`) — the C++ analogue of the
repo's "standard library only" rule (Go uses stdlib `testing`; this module uses `KATA_TEST` /
`EXPECT_*` / `KATA_MAIN`). Concurrency tests use its `kata::StartGate` (a `std::latch`) to release
worker threads together — the C++ mirror of Go's `close(start)` gate — and are verified under
**ThreadSanitizer**, the `go test -race` analogue.

## Katas

| # | Kata | Axis | Component & scenario | The real challenge |
|---|------|------|----------------------|--------------------|
| 1 | [`exchange_session`](practice/exchange_session/) | mechanics | Move-only RAII handle to one venue connection; connect on construct, release exactly once, moved into a registry. | Rule of five, move-only ownership, `noexcept` destructor. |
| 2 | [`tick_buffer`](practice/tick_buffer/) | mechanics | Fixed-capacity rolling window of the last N ticks (VWAP / last-N), no heap churn per tick. | Raw aligned storage, placement-new, destroy-before-overwrite, ring math. |
| 3 | [`order_handle`](practice/order_handle/) | mechanics | Intrusive ref-counted handle to a pooled order shared by the book, id-index, and risk engine. | Rule of five on a shared resource, self-assignment safety, reclaim-exactly-once. |
| 4 | [`top_of_book`](practice/top_of_book/) | concurrency | Seqlock quote publisher: one writer publishes best bid/ask, many strategy readers never block it. | Seqlock protocol, acquire/release ordering, atomic payload (TSan-clean), torn-read avoidance. |
| 5 | [`feed_pipe`](practice/feed_pipe/) | concurrency | Wait-free SPSC ring from the feed-handler thread to the strategy thread. | Lock-free SPSC, release/acquire hand-off, one-empty-slot full/empty, false sharing. |

Each `practice/<kata>/README.md` is the prompt: scenario → problem → requirements → what you implement
→ the real challenge → run → reference + extension.

## Commands

Requires a C++20 compiler (Apple clang / GCC / Clang) and CMake ≥ 3.20.

```bash
cd cpp-katas
make solution        # build everything + run the reference suite (GREEN); also compiles the skeletons
make practice        # compile just the practice skeletons (proves they build; no tests)
make solution-tsan   # rebuild under ThreadSanitizer and run — the -race analogue

# or drive CMake/CTest directly:
cmake -B build && cmake --build build
ctest --test-dir build --output-on-failure
ctest --test-dir build -R top_of_book       # run one kata (≈ go test ./top_of_book/)
```

### Concurrency verification

The two concurrency katas (`top_of_book`, `feed_pipe`) ship a `*_stress_test.cpp` — a gated,
high-contention run whose **self-consistency invariant** catches torn/lost/duplicated data even
without a sanitizer. Run them under ThreadSanitizer for the data-race proof:

```bash
make solution-tsan   # cmake -B build-tsan -DKATAS_SANITIZE=thread && ctest --test-dir build-tsan
```

> Note: Apple's TSan runtime currently crashes at startup on some macOS/arm64 setups (a trivial
> two-thread program SIGSEGVs) — this is an environment issue, not the katas. The build wires TSan
> correctly and it runs on Linux/CI; locally, the invariant stress tests are the fallback.
