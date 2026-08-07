# Interviewer script — Limit Order Book

How to run this as the interviewer: start the candidate on Stage 1 only. Every ~20 min, once their
current design is working, bolt on the next stage. The goal is to watch how they handle a requirement
that **invalidates their previous design**, not whether they memorised a matching engine.

## Stage 1 — add / cancel / best quote
**Ask for:** rest orders, cancel by id, report best bid/ask. Say nothing about matching yet.
**Push on:** "What if the book is empty?" "What if I cancel an id that isn't there?" "Two orders at the
same price — which is the head?" (forces them to commit to FIFO now, which pays off in Stage 2).
**Strong:** reaches for `TreeMap<price, deque>` per side immediately; prices as `long` ticks with a
one-line justification ("float equality is a trap"); an id→node map so cancel is O(1) to find.
**Weak:** a single `ArrayList` scanned linearly for best/cancel; `double` prices; no empty-book handling.

## Stage 2 — matching with partial fills
**Bolt on:** "Now orders can cross — a buy at or above the best ask should trade." This breaks a
Stage-1 design that only *stores*. Watch them turn `submit` into a loop.
**Push on:** "At what price does it trade — the incoming or the resting?" (resting/maker). "A buy for
100 lots against 30 resting — what happens to the other 70?" (rests). "The resting order was for 30 and
I take 10 — is it still at the front of the queue?" (yes; reduce qty, keep position). "Sweep two price
levels in one order." "Prove you never fill more than exists."
**Strong:** clean price-time loop; removes drained levels so best-quote never lies; `long` qty and can
say why; conservation holds; keeps the maker's queue position on a partial.
**Weak:** fills at the wrong (taker) price; forgets to remove empty levels (phantom best); loses the
maker's FIFO position; re-sorts the whole book each trade.

## Stage 3 — market orders & self-trade prevention
**Bolt on (a):** "Add market orders — no price, just take the best until filled." **(b):** "Compliance
says an account can't trade with itself — add a policy: cancel the resting order, or cancel the
incoming one."
**Push on:** "Market order into an empty book?" (no fills, doesn't rest). "Market order bigger than the
book — where does the remainder go?" (discarded, never rests). "With cancel-resting, what if that
exposes another same-account order behind it?" (keep applying). "Does STP change the maker price?"
**Strong:** factors the match loop so limit vs market differ only in the marketable check and the
rest-remainder step; STP is a small hook at the head of the loop; handles the empty-book and
oversized-market cases without special-casing spaghetti.
**Weak:** copy-pastes the match loop for market orders; STP bolted on with booleans that miss the
multi-maker case; market remainder accidentally rests.

## Stage 4 — thread-safe concurrent submission
**Bolt on:** "Now N venues submit concurrently — make it safe." This is where design maturity shows.
**Push on:** "Where exactly is the race?" (the read-modify-write of the book during a match). "Can two
matches run at once?" (no — matching is inherently sequential; that's the key insight). "How do you
keep `bestBid` consistent while a sweep is mid-flight?" "What about a cancel arriving as its order is
being filled?"
**Strong:** recognises matching must be serialised; a single lock (or a single matching thread fed by a
queue) around the whole match/cancel, reads under the same lock for a consistent snapshot; can *name*
the production design — a lock-free MPSC ring feeding one matching thread (no lock on the hot path,
mechanical-sympathy) — and the trade-off vs a `ReentrantLock`.
**Weak:** sprinkles `synchronized`/`ConcurrentHashMap` per-field (best-quote can still tear; a match
spanning two maps isn't atomic); claims a `ConcurrentSkipListMap` alone makes it safe (it doesn't —
the multi-step match is the critical section).

**If they finish early:** ask for the *hand-rolled* version — replace `ReentrantLock` with a single
matching thread and a bounded ring buffer for submissions, and discuss backpressure when the ring is
full. Or: iceberg orders, or a level-2 snapshot API (`Map<price, qty>` depth) done in O(1) per update.

## The reference in one line per stage
S1 `TreeMap<price, ArrayDeque>` per side + id→node map · S2 a price-time match loop with partial-fill
bookkeeping and empty-level removal · S3 the same loop with a marketable-check switch for market orders
and an STP hook at the head · S4 one `ReentrantLock` around the sequential match/cancel/read.
