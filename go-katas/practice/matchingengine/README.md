# Matching Engine

> The core of an exchange: match incoming orders against a limit order book under strict price-time priority — limit and market orders, partial fills, cancel/amend — then serialise concurrent order flow through a single matching loop so the book core needs no locks.

## The problem

An exchange's order book has two sides: **bids** (buyers) and **asks** (sellers).
Each side is a set of **price levels**, and each price level is a FIFO queue of
resting orders. An incoming order matches against the **opposite** side: a buy
matches asks, a sell matches bids. Whatever does not match immediately either
**rests** on the book (a limit order) or is **discarded** (a market order).

Matching must follow **price-time priority** — the fairness rule every regulated
exchange uses:

- **Price first.** The best price trades first (lowest ask for a buyer, highest
  bid for a seller).
- **Time second.** Among orders resting at the same price, the earliest arrival
  fills first.

This is a real-money system, so the design must be **deterministic** (the trades
depend only on the order arrival sequence) and must conserve quantity exactly (no
double-fill, no phantom fill).

## Requirements

- Crossing orders trade at the **maker (resting) price**, not the taker's price.
- A limit order's unfilled remainder **rests** on the book; a market order's
  remainder is **discarded** (market orders never rest).
- A large order **sweeps** multiple resting orders and price levels in
  price-then-time order, producing one `Trade` per resting order it consumes.
- `Cancel` removes a resting order; reduce-`Amend` keeps the order's time
  priority (increasing qty or re-pricing loses priority — reject or model as
  cancel+resubmit).
- `Best` reports the best bid and ask (ok=false unless both sides are populated).
- **Quantity conservation**: total bought == total sold, and no order fills beyond
  its original quantity.
- The `Engine` serialises concurrent `Submit`s through a single matching goroutine
  so the book needs no internal locks. `Close` stops cleanly: no goroutine leak,
  no send-on-closed panic, `Submit` after `Close` returns an error.
- No data races (`go test -race` clean). No `time.Sleep` for synchronization.

## What you implement

`OrderBook` (single-threaded core, no locking):

- `func NewOrderBook() *OrderBook`
- `func (b *OrderBook) Submit(o Order) []Trade`
- `func (b *OrderBook) Cancel(orderID string) bool`
- `func (b *OrderBook) Amend(orderID string, newQty int64) bool`
- `func (b *OrderBook) Best() (bestBid int64, bestAsk int64, ok bool)`

`Engine` (concurrency at the edge):

- `func NewEngine() *Engine`
- `func (e *Engine) Start()`
- `func (e *Engine) Submit(ctx context.Context, o Order) ([]Trade, error)`
- `func (e *Engine) Close()`

The domain types (`Side`, `OrderType`, `Order`, `Trade`, the error vars) are
provided.

## Stages

A numbered path to pace roughly 60 minutes:

1. **Limit orders + crossing at the maker price.** Build the book: sorted price
   levels per side (bids high→low, asks low→high), each level a FIFO queue.
   `Submit` matches a crossing limit against the opposite best level and emits a
   `Trade` at the maker's price; an unfilled remainder rests. `Best` reflects the
   book.
2. **Partial fills + FIFO time priority at a level.** Make `Submit` sweep across
   multiple resting orders and multiple price levels, decrementing remaining
   quantity on both sides and removing drained orders. Verify that at the same
   price the earliest arrival fills first.
3. **Market orders + cancel/amend.** A market order crosses any price and never
   rests (discard the remainder). Add `Cancel` (remove a resting order) and
   reduce-only `Amend` (keep priority; reject an increase).
4. **Concurrent submission via a serialised matching loop.** Wrap the book in an
   `Engine`: a single goroutine ranges a channel and applies orders one at a time;
   `Submit` sends an order + reply channel and awaits the trades. `Close` fences
   off `Submit` and stops the loop with no leak and no panic. Assert a
   deterministic aggregate from many concurrent submitters, race-clean.

## The real challenge

- **Price-time priority via FIFO levels.** Keep each side's price levels sorted
  (bids high→low, asks low→high) and each level a FIFO queue. Append on arrival,
  pop from the front when matching, walk best level first: the data structure IS
  the policy, which is what makes the outcome reproducible.
- **Integer ticks, not floats.** Price is an `int64` count of ticks. Binary
  floats cannot represent most decimal prices exactly, so float prices drift —
  equality fails, sums of fills do not reconcile, the "same" price hashes two
  ways. Integers compare and add exactly and make a clean level key.
- **Quantity conservation = no double-fill.** Each match decrements *both* the
  taker's and the maker's remaining quantity by the same amount, and a
  drained-to-zero resting order is removed. No quantity is created or matched
  twice; total bought always equals total sold.
- **Single-writer matching loop vs locking the book.** A matching engine is the
  textbook single-writer state machine: every op is a read-modify-write of the
  same book, and the *order* of ops is the result. A mutex around the book would
  serialise everything anyway plus add lock overhead and the risk of a forgotten
  critical section. Funnelling all orders through one goroutine gives the same
  serialisation, a lock-free core, and free determinism. The cost is one core per
  instrument — you scale by **sharding by instrument** (one Engine per symbol, a
  router hashing orders to shards), so different symbols match in parallel.

## Run

No tests ship with this kata — designing the tests is part of the exercise. Write
your own, then:

```bash
cd go-katas/practice && go test -race ./matchingengine/
```

## Reference

Compare against the answer key in `go-katas/solution/matchingengine/`.

Extension: add **self-trade prevention** (cancel or skip a resting order that
would match an incoming order from the same participant), OR **iceberg / hidden
-quantity orders** (a resting order that exposes only a small display quantity at
a time and replenishes from a hidden reserve, joining the back of the level — and
therefore losing time priority — on each refill).
