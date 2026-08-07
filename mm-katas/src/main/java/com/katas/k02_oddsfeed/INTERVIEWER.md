# Interviewer script — Odds feed parser

How to run this as the interviewer: start the candidate on Stage 1 only. Every ~20 min, once their
current design works, make the wire nastier. The point is to watch how each new reality
**invalidates the previous design** — a naive `split("\n")` per call dies in Stage 2, an
each-line-is-independent assumption dies in Stage 3 — not whether they can tokenise a string.

## Stage 1 — parse + dispatch
**Ask for:** parse `SEQ|BOOK|EVENT|MARKET|SELECTION|ODDS`; `onUpdate` for good lines, `onError` for
bad ones. Say nothing about chunking yet.
**Push on:** "A line with a trailing `|` — how many fields?" (7 → error; forces `split(-1)` not the
default which drops trailing empties). "Blank line?" (skip, not an error). "Why `BigDecimal` for
odds?" (float equality on prices is a trap). "What's in the error — can I find the line?" (a running
`lineNumber` + the raw line + a reason).
**Strong:** `split("\\|", -1)` with an explicit field-count check; a single per-line method returning
either an update or an error; a running `lineNumber` field, not a per-call index; never throws.
**Weak:** `split("\\|")` (silently drops the trailing empty → 7 fields looks like 6); `double` odds;
throws on bad input; resets the line counter each `feed()`.

## Stage 2 — malformed + partial lines
**Bolt on:** "The feed doesn't arrive as lines — it arrives as **chunks**. A line can be split across
two `feed()` calls, and one call can carry many lines." This breaks any `chunk.split("\n")`-per-call
design.
**Push on:** "A chunk with no newline at all?" (buffer, emit nothing). "Split exactly at the `|`? at
the `\n`?" "CRLF vs LF?" (both end a line). "A garbage line in the middle — do the lines after it
still parse?" (must not desync). "Did the reassembled line count as one line or two?"
**Strong:** an append-only buffer (`StringBuilder`), flush only on `\n`, keep the trailing remainder;
`\r` stripped via a line `strip()`; `lineNumber` incremented per completed line, so reassembly
counts once; an error just reports and returns — buffer untouched.
**Weak:** splits each chunk independently (loses or duplicates the boundary line); emits a partial
line early; a bad line throws and abandons the rest of the buffer; miscounts reassembled lines.

## Stage 3 — per-bookmaker seq + gap detection
**Bolt on:** "Each book has its own seq. Deliver in order, tell me when one is **missing**, and drop
**replays**." This breaks the each-line-is-independent assumption from Stages 1–2.
**Push on:** "Where does `expected` start?" (first seq seen for that book). "seq jumps from 2 to 5?"
(`onGap(book,3,5)` once, then deliver, `expected=6`). "The same seq twice?" (drop the second). "The
feed restarts and seq goes 6 then 3?" (3 ≤ lastDelivered → drop). "Two books interleaved?"
(independent state). "`int` or `long`?" (long — and mind `received+1` overflow at the top).
**Strong:** a `Map<String,Long>` of expected-next per book; three-way compare (`==`, `>`, `<=`); gap
fired once because `expected` jumps to `received+1`; malformed lines never touch seq state.
**Weak:** one global expected across all books; re-reports the gap on every subsequent line; delivers
duplicates; lets a backwards restart resurrect old updates; `int` seq.

## Stage 4 — backpressure via conflation
**Bolt on:** "The consumer is slower than the feed. You cannot buffer unboundedly — collapse a burst
for the same selection to just its **latest**, and it's producer-thread vs consumer-thread."
**Push on:** "offer 1,2,3 for one selection then poll?" (seq 3, pending 0). "A late lower-seq offer?"
(ignored). "How is memory bounded?" (by distinct selections, not #offers). "Producer and consumer on
two threads — where's the race?" (the read-modify-write of the per-selection latest + the pending
set). "Prove you never lose a selection's latest."
**Strong:** a `Map<selection, latest>` plus an order structure of pending selections; `offer` keeps
`max(seq)`; `poll` removes and returns; one lock (or a concurrent map + guarded queue) so the
compare-and-set of the latest is atomic; can argue no-lost-latest.
**Weak:** an unbounded queue (no conflation → the "backpressure" is a memory leak); per-field
synchronisation that lets a higher-seq offer be clobbered by a slower lower-seq one; conflates by the
wrong key (event, not selection).

**If they finish early:** ask for a bounded variant that drops-oldest under a hard cap, or a
watermark API (`Map<book, expectedSeq>`) exposed to a supervisor, or making the whole parser +
conflator a single pipeline stage feeding a matching engine (ties back to kata 01).

## The reference in one line per stage
S1 `split("\\|",-1)` + field-count/seq/odds checks + running `lineNumber` · S2 a `StringBuilder`
buffer flushed only on `\n`, trailing remainder retained · S3 a `Map<String,Long>` expected-next per
book with a three-way seq compare · S4 a `Map<selection,latest>` + pending deque under one lock,
keeping `max(seq)` per selection.
