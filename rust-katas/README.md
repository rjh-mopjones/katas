# Rust Katas

Senior-level Rust katas: the open-ended "implement this in ~an hour" tasks a Rust interview actually
asks. They come in three flavours — components of a low-latency **trading** platform; the canonical
CS/Rust **classics** (LRU cache, thread pool, expression evaluator, pub/sub bus); and real-world
**LLD scenarios** (connection pool, circuit breaker, parking lot, vending machine) like the Java
module's. Each maps to a real interview ask and drills a signature Rust topic — ownership & lifetimes,
traits & dispatch, enums & pattern matching, iterators, RAII, and concurrency.

> **Write your own tests.** The `practice/` side ships *without* tests on purpose — designing them
> (including the gated concurrency stress tests) is part of the exercise. The `solution/` twin carries
> reference tests to compare against.

## Layout

Two mirrored library crates in one Cargo workspace; each kata is a module at the same path in both.

```
rust-katas/
├── solution/   full reference implementations + built-in `#[test]` suites  (always GREEN)
└── practice/   the same modules as `todo!()` skeletons + per-kata READMEs;  NO tests
```

Testing is Rust's **built-in** `#[test]` / `cargo test` — no external harness, no third-party crates
(the whole module is std-only; nightly + Miri are needed only for the `spscring` capstone). Concurrency
tests gate their threads with `std::sync::Barrier` (the analogue of Go's `close(start)`) inside
`std::thread::scope`.

## Katas

| # | Kata | Theme | Interview ask | Rust topic |
|---|------|-------|---------------|------------|
| 1 | [`tickparser`](practice/src/tickparser/) | Trading | "parse this without allocating" | **Lifetimes** / zero-copy borrowing, `Result` + `?` |
| 2 | [`calc`](practice/src/calc/) | Classic | "write a calculator" | **Enums + recursion**, `Box` AST, recursive-descent parsing |
| 3 | [`candles`](practice/src/candles/) | Trading | "implement a custom iterator" | **`Iterator`** by hand, lazy streaming, generics |
| 4 | [`eventbus`](practice/src/eventbus/) | Classic | "build a pub/sub" | **Traits & dispatch**: `Box<dyn Fn>` vs generics, closures |
| 5 | [`lru`](practice/src/lru/) | Classic | "implement an LRU cache" | **Ownership**: the doubly-linked-list problem, index/slab, generics |
| 6 | [`threadpool`](practice/src/threadpool/) | Classic | "implement a thread pool" | **Message passing**: `mpsc` + `Box<dyn FnOnce+Send>`, `Drop` shutdown |
| 7 | [`positionbook`](practice/src/positionbook/) | Trading | "thread-safe shared state, no deadlock" | **`Arc<Mutex>`**: lost-update + deadlock-free lock ordering |
| 8 | [`orderstate`](practice/src/orderstate/) | Trading | "model a state machine" | **Enums + exhaustive `match`**, custom errors, typestate |
| 9 | [`spscring`](practice/src/spscring/) *(unsafe capstone)* | Trading | "lock-free ring / atomics" | **`unsafe`**: `UnsafeCell` + atomics, `Send`/`Sync`, **Miri**-verified |
| 10 | [`connpool`](practice/src/connpool/) | LLD scenario | "design a connection pool" | **RAII**: a borrow guard whose `Drop` returns the connection; `Condvar` blocking |
| 11 | [`circuitbreaker`](practice/src/circuitbreaker/) | LLD scenario | "design a circuit breaker" | **Enum state machine** + thread-safe `Mutex` state + injected clock |
| 12 | [`parkinglot`](practice/src/parkinglot/) | LLD scenario | "design a parking lot" | **Enums + exhaustive `match`** for fit rules, best-fit, ticket-as-capability |
| 13 | [`vending`](practice/src/vending/) | LLD scenario | "design a vending machine" | **Enum state machine** + data-carrying error enum + greedy change |

Each `practice/src/<kata>/README.md` is the prompt: scenario → problem → requirements → what you
implement → the real challenge → run → reference + extension.

## The Rust twist on concurrency

Rust eliminates **data races at compile time** (Send/Sync + the borrow checker), so unlike the Go
(`-race`) and C++ (TSan) modules there is no "prove the data race" exercise — a data race won't
compile. The concurrency katas target the bugs the compiler does *not* catch:

- `positionbook` — **deadlock** (lock ordering) and **lost update** (Mutex granularity).
- `threadpool` — **channel discipline** and graceful shutdown.
- `spscring` — real **UB in `unsafe`**, which is where **Miri** plays the role of ThreadSanitizer.

## Commands

Requires Rust (edition 2024, i.e. rustc ≥ 1.85). A `Makefile` wraps these.

```bash
cd rust-katas
make solution     # cargo test -p solution   — reference suite, GREEN
make practice     # cargo build -p practice   — skeletons compile (todo!() bodies)
make clippy       # cargo clippy -p solution -- -D warnings
cargo test -p solution lru        # one kata

# the unsafe capstone under Miri (the UB / data-race proof) — needs: rustup component add miri
make miri         # cargo +nightly miri test -p solution spscring
```
