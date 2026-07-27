# Feed Parser

> A streaming market-data feed parser: pipe-delimited text records -> typed `Quote`s, allocation-free per field, no exceptions on the parse path.

## The problem

A feed handler ingests a text feed of quote records — `SYMBOL|BID|ASK|QTY`, one per line — off a socket
or a replay file, and turns it into typed `Quote`s the book can consume. It is on the ingest hot path:
millions of lines, and a single malformed line (a truncated packet, a vendor bug, a stray comment) must
**not** throw, allocate wildly, or abort the stream. It must be reported with its line number and
skipped, while the good lines keep flowing.

The naive parse — `getline` into a string, `stringstream` / `stod` per field — is wrong three times over.
`stod` *throws* on bad input, so one malformed field aborts the stream unless every call is wrapped in
try/catch. `stringstream` allocates and is locale-sensitive (a comma-decimal locale silently misparses
`1.95`). And splitting into `std::string` fields copies bytes you already hold. The right tools are
`std::string_view` (slice fields with zero copies) and `std::from_chars` (parse a slice with no
allocation, no throw, locale-independent).

## Requirements

- Read `in` line by line with `std::getline`. The **physical line number is 1-based and counts every
  line**, including skipped ones.
- After trimming surrounding whitespace, a **blank** line or one starting with `#` is **skipped** — not
  a record, not an error (but it still advances the line counter).
- Otherwise the line must have **exactly 4** `|`-separated fields. Validate in this fixed order, stopping
  at the first failure (so each bad line yields exactly one error):
  1. field-count != 4 -> `WrongFieldCount`
  2. symbol empty (after trim) -> `EmptySymbol`
  3. bid not a float -> `InvalidBid`
  4. ask not a float -> `InvalidAsk`
  5. qty not a non-negative integer -> `InvalidQty` (a leading `-` must be rejected)
- Every `ParseError` carries the 1-based physical line number.
- `parse_feed` invokes exactly one of `on_quote` / `on_error` per non-skipped line. `parse_all` collects
  the results into a `ParseResult` (quotes + errors, in stream order).

### Canonical sample feed (the contract)

```
# market data feed
LIV-MUN|1.95|2.05|1000

ARS-CHE|1.50|1.60|500
|1.0|2.0|10
BAD|x|2.0|10
TOO|1.0|2.0
NEG|1.0|2.0|-5
```

Expected: `quotes = [{LIV-MUN,1.95,2.05,1000}, {ARS-CHE,1.50,1.60,500}]`;
`errors = [{5,EmptySymbol}, {6,InvalidBid}, {7,WrongFieldCount}, {8,InvalidQty}]`.

## What you implement

The two free functions in `katas`:

- `void parse_feed(std::istream& in, const std::function<void(std::size_t line, const Quote&)>& on_quote, const std::function<void(const ParseError&)>& on_error)`
- `ParseResult parse_all(std::istream& in)`

`ErrorKind`, `Quote`, `ParseError`, and `ParseResult` are provided verbatim. You design the
field-splitting and the number parsing.

## The real challenge

- **Zero-copy field slicing with `string_view`.** Split the trimmed line on `|` into field *views* into
  the line buffer — no `std::string` per field. Count the fields as you split so you can reject anything
  that isn't exactly 4.
- **`std::from_chars`, not `stod`/`stringstream`.** `from_chars(first, last, out)` returns a
  `std::from_chars_result` — a `std::errc` and a past-the-end pointer — and **never throws or allocates**
  and is **locale-independent**. Treat a field as valid only if `ec == std::errc{}` **and** the returned
  `ptr` reached the end of the field (so `1.0x` is rejected, not silently truncated to `1.0`).
- **Reject negative qty for free.** `from_chars` into an *unsigned* type refuses a leading `-`
  (`errc::invalid_argument`), so `-5` -> `InvalidQty` falls out of using `std::uint64_t` — no special case.
- **The string_view lifetime caveat.** `parse_feed` reuses one `getline` buffer, so every field view
  dangles the moment the next line is read. Anything that must outlive the line — the symbol stored in
  `Quote` — must be **copied** into an owning `std::string`. Handing back a view into the recycled buffer
  is a dangling read.
- **Money angle.** A parser that throws on a malformed line lets one corrupt packet abort the whole feed
  — the book goes stale and strategies trade on frozen prices. A locale-sensitive parse mis-marks the
  book by reading the wrong number. Line-numbered errors are what let ops pinpoint the offending record
  in a multi-gigabyte replay.

## Run

There are no tests here — writing them is part of the exercise. Add your own `feed_parser_test.cpp` in
this directory (use `../../solution/common/harness.hpp` and drive the parser with
`std::istringstream`), wire it into CMake, then:

```
cd cpp-katas && ctest --test-dir build -R feed_parser
```

## Reference

Worked solution: `cpp-katas/solution/feed_parser/`.

Extension: parse directly off the raw byte stream without the per-line `getline` copy (a hand-written
state machine over a buffered read), and add a `parse_view(std::string_view whole_feed, ...)` overload
that returns `Quote`s holding `string_view` symbols into the caller's buffer (zero symbol copies) — which
forces you to make the lifetime contract explicit in the API. Then add a strict/lenient mode where
lenient clamps a negative qty to 0 instead of erroring.

Background: [cppreference — std::from_chars](https://en.cppreference.com/w/cpp/utility/from_chars).
