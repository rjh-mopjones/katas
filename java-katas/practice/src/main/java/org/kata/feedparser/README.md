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

## Run
There are no tests here — **write your own** under `src/test/java/org/kata/feedparser/` to drive your implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you have your own attempt.
