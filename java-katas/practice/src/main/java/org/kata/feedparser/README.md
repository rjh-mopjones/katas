# Market-Data Feed Parser

> Parse a streaming text feed of pipe-delimited records into typed `Quote`s via a lazy `Stream`, reporting every malformed line with its physical position instead of throwing.

## The problem
A market-data feed arrives as text lines, one record per line: `SYMBOL|BID|ASK|QTY`. Turn that stream of raw lines into a stream of typed outcomes. Good lines become `Quote`s; bad lines become `ParseError`s that say *what* was wrong and *where*. A single malformed tick must never abort the stream and lose the good ticks behind it — errors are in-band values, not exceptions.

The feed also contains noise: blank lines and `#` comments. After trimming, a blank line or a line starting with `#` is **skipped** — it is neither a record nor an error — but it still counts toward the line number.

## Requirements
- Feed line format: `SYMBOL|BID|ASK|QTY`, split on `|` into **exactly four** fields.
- After trimming, a **blank** line or a line **starting with `#`** is **skipped** (produces nothing). It still advances the 1-based physical line counter.
- Validation order per record line — report only the **first** failure:
  1. not exactly 4 fields → `WRONG_FIELD_COUNT`
  2. empty symbol → `EMPTY_SYMBOL`
  3. bid not a `double` → `INVALID_BID`
  4. ask not a `double` → `INVALID_ASK`
  5. qty not a **non-negative** `long` → `INVALID_QTY`
- Every `ParseError` carries the **1-based physical line number** — counting *every* input line, including the skipped blank/comment lines.
- `parse` must be **lazy**: map an input `Stream<String>` to a `Stream<ParsedLine>` without materialising either, so short-circuiting terminals (`findFirst`, `limit`) and unbounded sources work.

### Canonical feed → expected output
```
# market data feed         (line 1, skipped)
LIV-MUN|1.95|2.05|1000     (line 2, quote)
                           (line 3, blank, skipped)
ARS-CHE|1.50|1.60|500      (line 4, quote)
|1.0|2.0|10                (line 5, EMPTY_SYMBOL)
BAD|x|2.0|10               (line 6, INVALID_BID)
TOO|1.0|2.0                (line 7, WRONG_FIELD_COUNT)
NEG|1.0|2.0|-5             (line 8, INVALID_QTY)
```
`quotes = [Quote("LIV-MUN",1.95,2.05,1000), Quote("ARS-CHE",1.50,1.60,500)]`
`errors = [(5,EMPTY_SYMBOL),(6,INVALID_BID),(7,WRONG_FIELD_COUNT),(8,INVALID_QTY)]`

## What you implement
Implement `FeedParser` from scratch — the public API:
- `static Stream<ParsedLine> parse(Stream<String> lines)` — the lazy mapping.
- `static ParseSummary parseAll(Stream<String> lines)` — drain into `(List<Quote>, List<ParseError>)`.

You design the internals: how you thread the physical line number through the stream, how you skip blank/comment lines while still counting them, and how you validate + coerce a line to a `ParsedLine`.

(`Quote`, `ErrorKind`, `ParseError` records/enum and the sealed `ParsedLine` — with `Ok`/`Err` — plus the `ParseSummary` record are provided as scaffolding.)

## The real challenge
- **Keep it lazy.** `Stream` has no element index, so thread the 1-based physical line number yourself — an `AtomicInteger` incremented once per input line inside the mapping step. This is only correct on an *ordered, sequential* pipeline: one increment per element in encounter order. Never `parallel()` this stream.
- **Skip without losing position.** Blank/`#` lines emit *nothing* yet must still bump the counter, so a later error keeps its true physical line. `mapMulti` (emit zero-or-one per input) is a clean lazy way to drop the skipped lines; a `map`-then-`filter` of an `Optional` also works.
- **`split("\\|", -1)` — the trailing-empty gotcha.** The default `split("\\|")` (limit 0) *discards trailing empty fields*: `"TOO|1.0|2.0|".split("\\|")` is length 3, silently swallowing a malformed trailing field. Use limit **-1** so the field count is exact and empty fields (including an empty symbol) survive. Escape the pipe (`"\\|"`) — a bare `|` is regex alternation.
- **Errors are values.** Model each outcome as the sealed `ParsedLine` (`Ok` / `Err`) and handle both arms with an exhaustive `switch` — the compiler rejects a forgotten arm, so no outcome is ever dropped.
- **Non-negative qty.** `Long.parseLong("-5")` succeeds and returns `-5`; the non-negative rule is a separate explicit guard, not a parse failure.

## Run
There are no tests here — **write your own** under `src/test/java/org/kata/feedparser/` to drive your implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/feedparser/`
- Java Interview Primer: Q90 (Streams / lazy pipelines), Q94 (`Collector`/reduction), sealed types & pattern matching (`switch` on sealed interfaces)
- Extension: support a quoted/escaped `|` inside a symbol, or emit a running `Stream<ParseSummary>` snapshot; add an `Iterator`/`BufferedReader.lines()` source adapter for a live socket.
