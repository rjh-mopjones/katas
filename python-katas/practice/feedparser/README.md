# Streaming Market-Data Feed Parser

> A market-data gateway receives quotes as a text firehose — `SYMBOL|BID|ASK|QTY` per line, peppered with comments, blanks, and malformed venue prints — turn it into typed quotes *as they arrive*, reporting every bad line by its physical line number, without ever buffering the whole feed.

## The problem

A market-data feed emits quotes as a text stream: one pipe-delimited record per line,
`SYMBOL|BID|ASK|QTY` (e.g. `LIV-MUN|1.95|2.05|1000`). The feed carries `#` comment lines and blank
separators, and — coming off a wire from many venues — some lines are malformed: a missing field, an
empty symbol, an unparseable price, a negative size. Your job is the parser that turns this line
stream into typed `Quote`s **as they arrive**, and never discards the whole batch because one line
was bad: each reject is reported with the exact physical line number.

## Requirements

- A feed is a sequence of text lines, each `SYMBOL|BID|ASK|QTY`.
- After trimming, a **blank** line or a line starting with `#` is **skipped** — not a record and not
  an error — but it **still counts** toward the 1-based physical line number.
- A record must have exactly four `|`-fields. Validate in a **fixed order** so the first problem
  wins deterministically:
  1. not exactly 4 fields → `WRONG_FIELD_COUNT`
  2. empty symbol (after trim) → `EMPTY_SYMBOL`
  3. `bid` not a float → `INVALID_BID`
  4. `ask` not a float → `INVALID_ASK`
  5. `qty` not a **non-negative** int → `INVALID_QTY`
- Every error carries the **1-based physical line number**.
- Each non-skipped line produces one `Parsed` with **exactly one** of `quote` / `error` set.
- Empty input yields nothing; a comment/blank-only feed yields nothing.

## What you implement

- `parse_feed(lines: Iterable[str]) -> Iterator[Parsed]` — a **lazy generator**, one `Parsed` per
  non-skipped line.
- `parse_all(lines: Iterable[str]) -> tuple[list[Quote], list[ParseError]]` — the eager collector.

The `Quote`, `ErrorKind`, `ParseError`, and `Parsed` types are provided. You design the parsing.

## The real challenge

- **Make `parse_feed` a generator, not a list-builder.** `yield` one `Parsed` per non-skipped line as
  you read it; never collect `lines` into a list first. The input is *any* `Iterable[str]` — a file
  object, a socket-line iterator, `splitlines()` — so a generator streams an unbounded feed in O(1)
  memory and stops the instant the consumer does (an `islice`/early `break` reads no further).
- **Line numbers are physical, not record-relative.** Count *every* line, including the skipped
  comments and blanks (`enumerate(lines, start=1)`), so an operator can find the reject in the raw
  capture. Skipping without advancing the counter is the classic bug.
- **Validation order is the contract.** Check field-count → empty-symbol → bid → ask → qty and stop
  at the first failure — a line that is both short *and* has a bad price is a `WRONG_FIELD_COUNT`, not
  an `INVALID_BID`. Use `str.split("|")`, then `float(...)` / `int(...)` in `try/except ValueError`;
  reject a **negative** qty (and a non-integer like `1.5`) as `INVALID_QTY`. Note `0` is valid.
- **Errors are values, not exceptions.** Model the reject as a `ParseError` you put inside a `Parsed`
  and `yield` — don't `raise`. One malformed venue line must not abort the other good ones; the
  caller decides whether to log, alert, or halt.
- **Money angle.** This is the ingest edge of a pricing stack — every downstream book and fill is
  priced off these quotes. Parsing lazily keeps a live feed at low latency; per-line errors mean one
  bad print never blacks out the market; rejecting a garbage price at the door stops it from ever
  mispricing the book.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_feedparser.py` in this
directory. Cover the canonical feed below (exact quotes + exact `(line, kind)` errors), each error
variant, empty input, a comment/blank-only feed, and **laziness** (pass a generator that raises if
fully consumed and assert `next()` still returns items incrementally). Then:

```
cd python-katas && .venv/bin/pytest practice/feedparser
```

Canonical sample feed (the output is the contract):

```
# market data feed
LIV-MUN|1.95|2.05|1000

ARS-CHE|1.50|1.60|500
|1.0|2.0|10
BAD|x|2.0|10
TOO|1.0|2.0
NEG|1.0|2.0|-5
```

Expected: `quotes = [Quote("LIV-MUN",1.95,2.05,1000), Quote("ARS-CHE",1.50,1.60,500)]`;
`errors = [(5,EMPTY_SYMBOL), (6,INVALID_BID), (7,WRONG_FIELD_COUNT), (8,INVALID_QTY)]`.

Compare against the reference: `.venv/bin/pytest solution/feedparser`.

## Reference

Worked solution: `solution/feedparser/`.

Extension: make the parser resumable across chunks (feed it successive `recv()` buffers, holding a
partial trailing line); or add a `parse_feed` variant that keys quotes by symbol and yields a
lazily-updated best-bid/best-offer per instrument as the feed streams.

Background: [Python generators](https://docs.python.org/3/howto/functional.html#generators).
