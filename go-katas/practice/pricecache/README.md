# Price Cache

> A low-latency sports-betting price cache: one feed writes the latest prices, many handlers read them — concurrently.

## The problem

A market-data feed goroutine continuously publishes the latest two-sided quote
(`Bid`, `Ask`, and a monotonic `Seq`) for each betting market. Meanwhile dozens
of HTTP handler goroutines read the current price for a market on every incoming
request. You need a cache that one writer and many readers can hammer
simultaneously without corrupting data or crashing the process.

## Requirements

- Store the latest `Price` per market string, overwriting on each update.
- `Get` returns the current price and whether the market is known.
- A missing market returns the zero `Price` and `false`.
- `Snapshot` returns the full set of current prices as a map the caller fully owns.
- Safe for one writer goroutine and many concurrent reader goroutines.

## What you implement

The exported API:

- `func NewPriceCache() *PriceCache`
- `func (c *PriceCache) Set(market string, p Price)`
- `func (c *PriceCache) Get(market string) (Price, bool)`
- `func (c *PriceCache) Snapshot() map[string]Price`

The `Price` struct is given. You design all the internals (the storage and the
synchronisation strategy).

## The real challenge

- A bare `map[string]Price` shared across goroutines is a **data race** under
  Go's memory model. A concurrent map read overlapping a map write is a fatal
  runtime error — `concurrent map read and map write` — that aborts the process.
- Even without the map crash, the multi-word `Price` value can **tear**: a read
  overlapping a write may see the new `Bid` with the old `Ask`/`Seq`. `Seq`
  exists so you (and your tests) can detect that the fields move as one unit.
- `go test -race` is the tool that **proves** the race exists in a naive version
  and proves your version is clean. Make it part of your loop.
- `RWMutex` vs `Mutex`: this feed is read-heavy (one writer, many readers), so an
  `RWMutex` lets readers run concurrently and only serialises on writes. A plain
  `Mutex` would queue readers behind each other. Know the trade-off (RWMutex has
  higher per-op overhead and can lose under heavy write contention).
- The **Snapshot aliasing trap**: returning the internal map directly lets callers
  iterate or mutate it without the lock — reintroducing the very race you fixed.
  Return a copy.

## Run

There are no tests here — designing the tests is part of the exercise. Write your
own in this same package directory (`pricecache_test.go`), including a gated
concurrent reader/writer test that would catch the race under `-race`. Then:

```
cd go-katas/practice && go test -race ./pricecache/
```

## Reference

Worked solution: `go-katas/solution/pricecache/`.

Extension: make the read path **lock-free** with `atomic.Pointer[map[string]Price]`
copy-on-write (writer clones, inserts, atomically swaps; readers do a single
atomic load and never block) and benchmark it against the `RWMutex` version.
