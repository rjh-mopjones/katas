# Limit Order Book

## Approach
The book is two `TreeMap<BigDecimal, Deque<Order>>`s: `buys` ordered by `Comparator.reverseOrder()`
so `firstEntry()` is always the highest bid, `sells` in natural order so `firstEntry()` is always the
lowest ask. Each price level is an `ArrayDeque<Order>` — `peek`/`poll` at the head give O(1) FIFO,
which is exactly what price-time priority needs within a level. A parallel `UUID → Order` map
(`openOrders`) exists purely so `cancel` doesn't have to scan every level: it turns cancellation into
one map lookup plus a bounded scan of a single price level.

`submit` stamps the instant *before* taking the lock (keeps the critical section short, and mirrors
how real exchanges timestamp at receipt), then runs `match`: an outer loop walks the opposite book
best-price-first, and for each level an inner loop drains the FIFO deque, printing trades at the
resting order's price (price improvement for the aggressor) until either the aggressor is exhausted or
the level no longer crosses — since price levels are ordered, the first level that fails to cross means
no deeper level can cross either, so the loop can stop immediately. A partially-filled resting order is
never mutated in place — `Order` is immutable, so the head of the deque is replaced via `withQty`,
preserving its original timestamp and queue position (only the displayed quantity shrinks). Anything
left of the aggressor after matching rests via `rest()`, which lazily creates the price-level deque and
adds to the tail.

Concurrency is a single `ReentrantLock` guarding all mutating operations. Matching is inherently
sequential — each fill changes book state that the very next match decision depends on — so
fine-grained per-level locking would add contention without buying any real parallelism; a single
aggressor routinely walks multiple levels in one call anyway. This is the same single-writer principle
behind the LMAX Disruptor: one thread processes the sequential match loop, and parallelism (when
needed) comes from sharding across symbols, not from parallelising a single symbol's book.

## The real challenge
- **Data structure choices.** Bids use a reverse-ordered `TreeMap` so `firstEntry()` is always the best (highest) bid. Asks use natural-order `TreeMap` so `firstEntry()` is always the best (lowest) ask. Each price level holds an `ArrayDeque<Order>` — `peek`/`poll` at the head give FIFO in O(1), and de-duplication of price levels is automatic.
- **Flat id index for cancellation.** The deque-of-orders layout is optimal for matching but O(n) to search by id. A parallel `UUID → Order` map makes `cancel` O(log p) (one TreeMap lookup for the price level) rather than a full book scan.
- **Partial fill bookkeeping.** When a resting order is partially consumed, replace the head of the deque with a new `Order` instance (via `withQty`) — do not mutate in place. The order keeps its original timestamp and stays at the front; only the quantity changes.
- **Single-writer lock.** Each fill changes book state, and the very next match decision depends on that change. Fine-grained per-level locking adds contention without benefit here. One `ReentrantLock` for the whole book is the correct trade-off.
- **`BigDecimal` for prices.** Binary float rounding in a matching engine accumulates into real P&L errors. Always use `BigDecimal` and `compareTo`, never `==` or `double` arithmetic.

## Common mistakes & senior signal
- Mutating a resting `Order` in place on a partial fill instead of replacing it — breaks immutability and risks losing its original timestamp/queue position.
- Forgetting to remove an emptied price level from the `TreeMap` — `bestBid`/`bestAsk` then return a phantom level with no liquidity.
- Comparing prices with `==` or `double` instead of `BigDecimal.compareTo` — silent rounding drift in a matching engine is a real money bug.
- Reaching for per-price-level locking "for scalability" without noticing the sequential dependency between fills — it adds contention, not throughput.
- A naive `cancel` that scans every price level (O(n)) instead of using a flat id index (O(log p)) — fine for a toy book, wrong for anything resembling production.
- Not recognising that trades print at the *resting* price, not the aggressor's — getting this backwards silently removes price improvement from every fill.

## Extensions
- **IOC (Immediate-Or-Cancel):** fill what crosses now, discard the residual instead of resting it.
- **FOK (Fill-Or-Kill):** pre-walk the opposite book; if the full qty can't be filled, reject the order entirely without producing partial fills.
- **Market orders:** no price limit — match against best available until the qty is exhausted (and typically cancel any unfilled residual).
- **Iceberg orders:** only a small "display" qty is visible at the level; on fill, a fresh slice is refreshed from the hidden reserve, losing time priority each refresh.
- **Self-trade prevention:** reject (or cancel oldest) when both sides of a potential match belong to the same account.
- **Pro-rata matching:** alternative to price-time priority used on some futures (e.g. short-end rates). At a level, fills are allocated proportional to resting qty rather than first-come-first-served.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/orderbook/`)
- Java Interview Primer: Q30 (TreeMap/sorted maps), Q31 (Comparable/Comparator), Q155 (PriorityQueue)
