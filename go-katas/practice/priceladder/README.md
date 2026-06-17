# Price Ladder

> A market price ladder where many feed updates apply *relative* odds adjustments to a market's price — concurrently.

## The problem

A betting market has a current price. A stream of feed updates apply *relative*
adjustments to it: "shorten this market by 0.05", "drift it by 0.10", and so on.
Many feed goroutines apply these adjustments to the same market at the same time.
You need a ladder that lets every adjustment land — the final price must reflect
the sum of all deltas applied, no matter how the goroutines interleave.

## Requirements

- Track the current price per market string.
- `Adjust(market, delta)` applies a *relative* change: current price += delta,
  treating an absent market as starting from 0.
- `Set(market, price)` assigns an absolute price, overwriting any previous value.
- `Price(market)` returns the current price and whether the market is known; a
  missing market returns `0` and `false`.
- Safe for many goroutines adjusting the same market concurrently — every delta
  applied exactly once.

## What you implement

The exported API:

- `func NewLadder() *Ladder`
- `func (l *Ladder) Adjust(market string, delta float64)`
- `func (l *Ladder) Set(market string, price float64)`
- `func (l *Ladder) Price(market string) (float64, bool)`

You design all the internals (the storage and the synchronisation strategy).

## The real challenge

- A relative `Adjust` is a **read-modify-write**: read the current price, add the
  delta, write it back. If that RMW is not atomic, concurrent adjustments **lose
  updates** — two goroutines both read `2.00`, both add their own delta, both
  write, and one delta is silently overwritten. The final price is wrong. In a
  real-money market a price that doesn't reflect every adjustment **mis-prices
  risk** — a correctness defect, not a cosmetic one.
- The trap: the lock must span the **entire** read+modify+write. A "get-then-set"
  assembled from two *separately* locked operations (lock-read-unlock … then
  lock-write-unlock) **still loses updates**, because another goroutine slips in
  between the read and the write. Read, add, and write must all sit inside one
  held critical section.
- You **cannot** make this lock-free with a bare atomic on a `float64`:
  `sync/atomic` has no atomic float add. The lock-free recipes are a
  compare-and-swap loop on the bit pattern (`atomic.Uint64` over
  `math.Float64bits`, retry on contention), or — better — represent money as
  integer **ticks** so a single `atomic.AddInt64` does the whole adjustment
  wait-free.
- **Striped locks**: if you keep the mutex, shard markets across N locks so
  adjustments to *different* markets don't contend on one global lock.

## Run

There are no tests here — designing the tests is part of the exercise. Write your
own in this same package directory (`priceladder_test.go`), including a gated
concurrent test where G goroutines each apply N `+1` adjustments to one market and
assert the final price is `G*N` (this fails on a non-atomic RMW). Then:

```
cd go-katas/practice && go test -race ./priceladder/
```

## Reference

Worked solution: `go-katas/solution/priceladder/`.

Extension: make the hot path **lock-free** using an integer tick representation
(money as `int64` hundredths) plus `atomic.AddInt64`, and benchmark it against the
mutex version.
