# Kata 02 · Odds feed parser — streaming, resequencing, backpressure

**Difficulty:** hard · **Total target:** 90 min · **Class:** streaming & state

> Consume a bookmaker's raw odds feed off the wire. The interviewer starts you on a clean
> line-parser and, roughly every 20 minutes, makes the wire nastier: it arrives in ragged chunks,
> lines go missing or repeat, and the consumer can't keep up.

You implement `OddsFeedParser` (Stages 1–3) and `ConflatingBuffer` (Stage 4). The wire format is one
update per line:

```
SEQ|BOOK|EVENT|MARKET|SELECTION|ODDS
```

exactly six pipe-separated fields. `SEQ` is a `long`, `ODDS` is a **`BigDecimal`** (decimal odds are
exact prices — never `double`). A blank line (after trimming) is skipped, not an error. `lineNumber`
is **1-based and runs across every `feed()` call**.

`OddsUpdate`, `ParseError`, `FeedListener`, `OddsFeedParser`, `ConflatingBuffer` are provided; only
the two SUT classes' method bodies are yours. Work stage by stage — run `./kata 02` to start the
clock and reveal Stage 1; later stages unlock as you pass each one.

## Stage 1 — parse + dispatch · target 20 min

`feed()` is handed whole, newline-terminated lines. Parse each and dispatch:

- a well-formed line → `onUpdate(new OddsUpdate(seq, book, event, market, selection, odds))`;
- a malformed line → `onError(new ParseError(lineNumber, rawLine, reason))` with a helpful reason
  (wrong field count / bad seq / bad odds). **Never throw on bad input.**

Watch: an empty/blank line is **skipped** (not an error); a trailing delimiter makes **7** fields →
error; surrounding/extra whitespace is trimmed; `ODDS` is parsed to `BigDecimal`, not `double`.
Enable Stage 2 tests only when Stage 1 is green.

## Stage 2 — malformed + partial lines · target 20 min

Now `feed()` is handed arbitrary **chunks** of the stream. A line may be split across two (or more)
`feed()` calls; buffer the incomplete trailing line until the rest arrives, then parse it **once**,
whole.

- a chunk with no newline yet → buffer it, emit nothing;
- a line split exactly at a `|` or at the `\n` still parses once;
- both `\r\n` (CRLF) and `\n` (LF) end a line;
- a malformed line mid-stream must **not desync** the lines after it.

Watch: don't double-count `lineNumber` when a line is reassembled from pieces; don't emit a partial
line early; keep the buffer consistent after an error.

## Stage 3 — per-bookmaker seq + gap detection · target 25 min

Each bookmaker numbers its own updates. Track the **expected next seq per book** (it starts at the
first seq you see for that book) and resequence:

- `received == expected` → deliver, `expected++`;
- `received > expected` → `onGap(book, expected, received)` **once**, then deliver and set
  `expected = received + 1`;
- `received <= lastDelivered` → a duplicate or an old/replayed line → **drop it** (no `onUpdate`).

Watch: a duplicated seq is dropped; a gap is reported exactly once; `seq` is a `long` (mind
overflow); a book whose seq jumps **backwards** (feed restart) is treated as old and dropped; the
**first** message for a book always delivers; books are independent.

## Stage 4 — backpressure via conflation · target 25 min

The consumer is slower than the producer. Implement `ConflatingBuffer` so a burst of updates for the
same selection collapses to only the latest.

- `offer(update)` conflates by `selection`, keeping the **highest-seq** update; an offer whose seq is
  not newer than the buffered one for that selection is ignored.
- `poll()` returns the latest-per-selection update, or empty when nothing is pending.
- `pending()` is the number of distinct selections buffered — memory is bounded by that, **not** by
  the number of offers.
- Producer and consumer run on **different threads**; make it thread-safe with no lost latest.

Watch: offer seq 1,2,3 for one selection then `poll()` → seq 3, `pending()` 0; a lower-seq offer
after a higher one is ignored; a concurrent producer/consumer must never lose a selection's latest
and must leave a consistent final state.

## Run

```
./kata 02           # start: reveals Stage 1, starts the stopwatch
./kata 02 check     # run the current stage; on green, unlock + reveal the next stage
```

Reference (after you've worked it): `solutions/k02_oddsfeed/` — `OddsFeedParser.java`,
`ConflatingBuffer.java` + `NOTES.md` walking the design pivot each stage forces.
