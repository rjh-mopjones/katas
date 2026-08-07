# Reference notes — Odds feed parser

`OddsFeedParser.java` passes Stage 1 → Stage 3; `ConflatingBuffer.java` is Stage 4. Prove it:
`solutions/verify.sh 02`. This walks the **design pivot each stage forces** — the point of the kata.

## Data structures (chosen small, and they hold up)
- `buffer`: a `StringBuilder` the stream is appended to; lines are cut only on `'\n'`, and whatever
  follows the last `'\n'` stays buffered as the incomplete remainder.
- `lineNumber`: a single running counter incremented once per **completed** line, so a line
  reassembled from several chunks counts once, across all `feed()` calls.
- `expectedByBook`: `Map<String,Long>` — the next seq we expect from each book. Absence means "first
  message for this book"; presence − 1 is effectively the last delivered seq.

## Stage 1 → 2: from *lines* to *a stream of bytes*
Stage 1's temptation is `chunk.split("\n")` per `feed()`. Stage 2 kills it: a line can straddle two
calls. The pivot is to stop treating a `feed()` as a unit of lines and treat the parser as a stream
machine — append to a buffer, flush only on a terminator, keep the tail. Everything else stays: the
same `processLine` handles both stages. Two traps the tests pin:
- **`split("\\|", -1)`** — the default `split` drops trailing empty fields, so a trailing-`|` line
  (7 fields) would masquerade as 6. The `-1` limit keeps them, so field-count validation actually works.
- **`line.strip()`** removes a CRLF's `\r` and any extra whitespace, so LF and CRLF both terminate a
  line and padded fields parse — without special-casing `\r`.
An error just calls `onError` and returns; the buffer is never touched, so a garbage line can't
desync the good lines after it.

## Stage 2 → 3: each line is no longer independent
Stages 1–2 parse each line in isolation. Stage 3 says the stream has **memory**: a per-book expected
seq. `resequenceAndDispatch` is a three-way compare against `expected`:
- `seq < expected` → duplicate or old/replayed → **drop** (no `onUpdate`). This one branch covers
  both a repeated seq and a backwards feed restart.
- `seq > expected` → a hole: `onGap(book, expected, seq)` **once**, then deliver. Firing once falls
  out of jumping `expected` straight to `seq + 1` — the next contiguous line matches and no gap
  re-fires.
- `seq == expected` (including the very first message, where `expected` is seeded to the seq) →
  deliver, advance.
Malformed lines never reach this method, so they can't perturb seq state. `seq` is `long`; `seq + 1`
overflows only at `Long.MAX_VALUE` — noted, not defended (a real feed never gets there).

## Stage 3 → 4: bound the memory under backpressure
A slow consumer + an unbounded queue is just a memory leak wearing a hat. The pivot is to **conflate**:
key the buffer by `selection` and keep only the latest. `offer` keeps `max(seq)` per selection;
`poll` removes and returns; `pending()` is the map size, so memory is bounded by distinct selections,
not by offer count. Producer and consumer are on different threads and the race is the
read-modify-write of the per-selection latest **plus** the pending set — a per-field concurrent map
alone would let a slow lower-seq offer clobber a higher one, or desync the queue from the map. One
`ReentrantLock` makes offer's compare-keep-highest and poll's remove-return atomic, which is what
"no lost latest" needs.

**Production alternative (say this in the interview):** a lock-free `ConcurrentHashMap<selection,
AtomicReference<OddsUpdate>>` with a CAS-max on offer and a separate MPSC ring of "dirty" selections
for the consumer to drain — no lock on the hot path. The `ReentrantLock` is the right *interview*
answer under time pressure; the CAS-map + dirty-ring is the right *production* answer.

## Edge cases the tests pin
Blank line skipped · trailing delimiter → 7 fields → error · bad seq / bad odds reported not thrown ·
odds as `BigDecimal` · running `lineNumber` across `feed()` calls · line split across chunks (at `|`
and at `\n`) reassembles once · chunk with no newline emits nothing · CRLF and LF both terminate · a
malformed line mid-stream doesn't desync · gap reported once · duplicate dropped · backwards restart
dropped · first message per book delivered · books independent · conflation keeps highest seq · lower
-seq offer ignored · `pending()` counts distinct selections · concurrent producer/consumer loses no
latest and ends consistent.
