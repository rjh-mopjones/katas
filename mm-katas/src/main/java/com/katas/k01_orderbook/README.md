# Kata 01 · Limit Order Book — price-time priority

**Difficulty:** hard · **Total target:** 90 min · **Class:** streaming & state

> Build the matching core of an exchange for one instrument. The interviewer will keep the book on one
> instrument and bolt on a new requirement roughly every 20 minutes.

You implement `OrderBook`. Prices are integer **ticks** (never floating point). Work stage by stage —
run `./kata 01` to start the clock and reveal Stage 1; later stages unlock as you pass each one.

`OrderBook`, `Order`, `Fill`, `Side`, `OrderType`, `StpPolicy` are provided; only `OrderBook`'s methods
are yours to write.

## Stage 1 — add, cancel, best quote · target 20 min

Maintain resting orders and report the top of book. Orders in this stage never cross.

- `submit(order)` rests a limit order in the book.
- `cancel(id)` removes a resting order; returns `true` if one was removed, `false` if the id is unknown
  or already gone.
- `bestBid()` / `bestAsk()` return the best resting price (`OptionalLong`), empty on an empty side.
- Among orders at the same price, preserve **first-in-first-out** order (it matters later).

Watch: an empty book (no NPE, return empty), cancelling an unknown id, and two orders at the same price
(cancelling one must leave the level intact). Enable Stage 2 tests only when Stage 1 is green.

## Stage 2 — matching with partial fills · target 20 min

Now orders can cross. `submit` must match a marketable order against the opposite side and return the
`Fill`s in execution order; the unmatched remainder of a limit order **rests**.

- Best price first; within a price level, **FIFO** (price-time priority).
- Trades execute at the **resting (maker)** price, not the incoming price.
- A partial fill reduces the resting order's remaining quantity and keeps its place in the queue.
- A fully-consumed price level must disappear — `bestAsk()`/`bestBid()` must never report an empty level.

Watch: **quantity conservation** — never fill more than exists; a marketable order that sweeps several
levels; an exact fill that empties the book; use `long` for quantities (a busy book overflows `int`).

## Stage 3 — market orders & self-trade prevention · target 25 min

- **Market orders** (`Order.market(...)`): no price; take the best available levels until filled or the
  book is empty. Any unfilled remainder is **discarded** (a market order never rests).
- **Self-trade prevention**: when the book is constructed with a `StpPolicy` and an incoming order would
  match a resting order from the **same account**, apply the policy instead of self-matching:
  - `CANCEL_RESTING` — cancel the resting order and keep matching the incoming order against the rest.
  - `CANCEL_NEWEST` — stop matching the incoming order; its remainder is dropped (prior fills stand).

Watch: a market order into an empty book (no fills, nothing rests); STP removing the FIFO head
mid-sweep; STP interacting with a multi-level sweep.

## Stage 4 — thread-safe concurrent submission · target 25 min

Many threads call `submit`/`cancel`/`bestBid`/`bestAsk` at once. The book must stay correct: no
over-fills, no lost fills, and a best-quote read must see a consistent snapshot.

- Total traded quantity against a resting order must exactly equal what was available — never more.
- A `cancel` racing a fill of the same order must resolve atomically (one wins; no corruption).

Watch: the matching loop is inherently sequential — decide your concurrency strategy deliberately and
be ready to justify it (see the interviewer notes on the lock-free single-writer alternative).

## Run

```
./kata 01           # start: reveals Stage 1, starts the stopwatch
./kata 01 check     # run the current stage; on green, unlock + reveal the next stage
```

Reference (after you've worked it): `solutions/k01_orderbook/` — `OrderBook.java` + `NOTES.md` walking
the design pivot each stage forces.
