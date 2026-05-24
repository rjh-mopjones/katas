# Limit Order Book

> Build a continuous double-auction matching engine with price-time priority, partial fills, and cancellation.

## The problem
Implement a limit order book for a single trading symbol. Incoming orders are matched against resting orders on the opposite side: a buy order lifts asks from lowest price up; a sell order hits bids from highest price down. An order that is not fully consumed by matching rests on its own side at its limit price, waiting for a future aggressor. All fills emit `Trade` records. Orders can be cancelled before they are filled.

## Requirements
- Matching is price-time priority: among orders at the same price level, the one submitted earliest matches first (FIFO within a level).
- A buy order crosses when its price is >= the best ask; a sell order crosses when its price is <= the best bid.
- Trades execute at the **resting** order's price, not the aggressor's (price improvement).
- An aggressor that is not fully consumed after matching rests the unfilled residual at its limit price.
- Partial fills of resting orders preserve time priority: the partially filled order stays at the head of its price-level queue with reduced quantity.
- `cancel(orderId)` returns `true` if the order was open and removed, `false` if already filled, cancelled, or unknown.
- Empty price levels must be removed from the book immediately after they drain, so `bestBid()` and `bestAsk()` never return a phantom level.
- All operations are serialised on a single lock (the matching loop is inherently sequential).

## What you implement
Implement `OrderBook` from scratch — the public API (`submit`, `cancel`, `bestBid`, `bestAsk`). You design the internal data structures (bid/ask books, price-level queues, id index), the matching loop, the rest helper, and the locking strategy yourself.

(`Order` record, `Trade` record, and `Side` enum are provided as fully working scaffolding.)

## The real challenge
- **Data structure choices.** Bids use a reverse-ordered `TreeMap` so `firstEntry()` is always the best (highest) bid. Asks use natural-order `TreeMap` so `firstEntry()` is always the best (lowest) ask. Each price level holds an `ArrayDeque<Order>` — `peek`/`poll` at the head give FIFO in O(1), and de-duplication of price levels is automatic.
- **Flat id index for cancellation.** The deque-of-orders layout is optimal for matching but O(n) to search by id. A parallel `UUID → Order` map makes `cancel` O(log p) (one TreeMap lookup for the price level) rather than a full book scan.
- **Partial fill bookkeeping.** When a resting order is partially consumed, replace the head of the deque with a new `Order` instance (via `withQty`) — do not mutate in place. The order keeps its original timestamp and stays at the front; only the quantity changes.
- **Single-writer lock.** Each fill changes book state, and the very next match decision depends on that change. Fine-grained per-level locking adds contention without benefit here. One `ReentrantLock` for the whole book is the correct trade-off.
- **`BigDecimal` for prices.** Binary float rounding in a matching engine accumulates into real P&L errors. Always use `BigDecimal` and `compareTo`, never `==` or `double` arithmetic.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/orderbook/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/orderbook/`
- Java Interview Primer: Q30 (TreeMap/sorted maps), Q31 (Comparable/Comparator), Q155 (PriorityQueue)
