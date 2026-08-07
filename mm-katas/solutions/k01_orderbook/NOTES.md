# Reference notes — Limit Order Book

`OrderBook.java` in this directory passes Stage 1 → Stage 4. Prove it: `solutions/verify.sh 01`.
This walks the **design pivot each stage forces** — the point of the kata.

## Data structures (chosen in Stage 1, and they hold up)
- `bids`: `TreeMap<Long, ArrayDeque<Resting>>` with `Comparator.reverseOrder()` → `firstKey()` is the
  **highest** bid. `asks`: natural order → `firstKey()` is the **lowest** ask. Best quote is O(1)-ish.
- Each price level is an `ArrayDeque` → FIFO (price-**time** priority) with O(1) head access.
- `byId`: `HashMap<Long, Resting>` so `cancel` finds the node without scanning. `Resting` is mutable so
  a partial fill just shrinks `remaining` in place, keeping queue position.
- Prices/quantities are `long`. Float prices would make `==` comparisons unsafe; `int` quantities
  overflow on a busy book.

## Stage 1 → 2: from *store* to *match*
Stage 1's `submit` is one line (`rest(...)`). Stage 2 makes it a loop that, while the incoming order is
marketable against the best opposite level, trades against the head maker: `min(remaining, maker.remaining)`
at the **maker's** price, then either advances past a drained maker (and removes an emptied level so the
best quote never lies) or reduces the maker in place. The unmatched remainder of a *limit* order rests.
The Stage-1 data structures were already right — the pivot is behavioural, and it rewards having
committed to FIFO levels in Stage 1.

## Stage 2 → 3: market orders & STP, as small deltas
Two hooks, no new loop:
- **Market vs limit** differ only in the *marketable* test. `crosses(...)` gates a limit order; a market
  order skips that gate (always takes the best). The rest-remainder step runs only for `LIMIT` — a
  market remainder is discarded (`match` returns it, but `submit` ignores it for market).
- **STP** is a check at the head of the loop: if the head maker shares the taker's account,
  `CANCEL_RESTING` removes that maker and retries the level; `CANCEL_NEWEST` returns 0 (drop the taker's
  remainder — prior fills against *other* accounts stand, and nothing rests).

A candidate who copy-pasted the loop for market orders pays for it here; one who factored the marketable
check adds ~10 lines.

## Stage 3 → 4: serialise the sequential core
The race is the multi-step read-modify-write inside `match` (peek best, trade, remove level, rest
remainder) — not any single field. Per-field `ConcurrentHashMap`/`synchronized` cannot make that atomic,
and `bestBid` could read a half-updated book. The fix is to recognise **matching is inherently
sequential** and serialise the whole critical section with one `ReentrantLock`; reads take the same lock
for a consistent snapshot. Conservation then falls out for free (Stage 4's stress test buys 16,000
against a 10,000 ask and asserts exactly 10,000 trade).

**Production alternative (say this in the interview):** drop the lock from the hot path — a lock-free
MPSC ring buffer of submissions feeding a **single matching thread**. No contention, cache-friendly, and
the natural place to add backpressure (what to do when the ring is full). The `ReentrantLock` is the
right *interview* answer under time pressure; the ring is the right *production* answer.

## Edge cases the tests pin
Empty-book best quote · cancel-unknown-id · FIFO within a level · trade at maker price · partial-fill
queue position · empty-level removal (no phantom best) · quantity conservation / no over-fill · `long`
overflow avoidance · market-into-empty-book · market remainder not resting · STP head removal mid-sweep
· concurrent conservation · cancel racing a fill.
