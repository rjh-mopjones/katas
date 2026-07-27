# Feed Parser

> A streaming market-data feed parser: turn a pipe-delimited price feed into typed quotes, one line at a time, without slurping the whole thing into memory.

## The problem

A price vendor drops a text feed of two-sided quotes: one record per line,
`SYMBOL|BID|ASK|QTY`. A day's tick feed is far too big to read into memory at
once, and it is dirty — truncated records, missing symbols, garbage prices. You
need a parser that streams the input (bounded memory, quotes available before
EOF), decodes each good line into a typed `Quote`, and reports each bad line with
enough context to find it — without letting one malformed record abort the rest.

## Requirements

- Read `SYMBOL|BID|ASK|QTY`, one record per line, **streaming** — never load the
  whole input at once.
- After trimming surrounding whitespace, a **blank** line or one starting with
  `#` is **skipped**: not a record, not an error — but it still counts toward the
  1-based physical line number.
- A record must have **exactly 4** `|`-separated fields. Validate in this fixed
  order: field-count → empty-symbol → bid → ask → qty.
  - not exactly 4 fields → `WrongFieldCount`
  - symbol empty → `EmptySymbol`
  - bid not a float → `InvalidBid`; ask not a float → `InvalidAsk`;
    qty not a **non-negative integer** → `InvalidQty`
- Every error carries the **1-based physical line number** (counting skipped lines).
- Each non-skipped line yields **exactly one** of a valid `Quote` or a `*ParseError`.

## What you implement

The exported API (`Quote`, `ErrKind` + its constants, and `ParseError` are given):

- `func (k ErrKind) String() string`
- `func (e *ParseError) Error() string`
- `func Parse(r io.Reader, fn func(line int, q Quote, err *ParseError)) error`
  — calls `fn` once per non-skipped line, in order, with the physical line
  number; returns only a scanner/IO error.
- `func ParseAll(r io.Reader) (quotes []Quote, errs []ParseError, scanErr error)`
  — a convenience collector built on `Parse`.

## The real challenge

- **`bufio.Scanner` streaming, not slurping.** Reach for `bufio.NewScanner` and
  loop on `Scan()`/`Text()` so memory stays bounded to a single line — do **not**
  `io.ReadAll` and split. Write `ParseAll` on top of `Parse`, not the reverse:
  the streaming primitive is the foundation.
- **`strconv`, no regex.** Parse fields with `strconv.ParseFloat` and
  `strconv.ParseUint` (base 10, 64-bit). `ParseUint` rejects a leading `-` for
  free, so negative quantities fall out as `InvalidQty` without a special case.
- **Line-numbered errors.** Increment the counter for *every* physical line,
  including the blank/comment lines you skip, so the reported number matches what
  an operator sees in an editor. Stamp each `ParseError` with it.
- **The symbol is an owned copy.** `Scanner.Text()` returns a fresh Go string, so
  the `Symbol` you store stays valid after the scanner advances — no deliberate
  copy needed. Contrast a zero-copy parser (C/Rust, or Go's `Scanner.Bytes()`)
  where the symbol would alias a buffer the next read overwrites and you'd have to
  copy it yourself. Here you trade one allocation per line for that safety.

## Run

There are no tests here — designing the tests is part of the exercise. Write your
own in this same package directory (`feedparser_test.go`). Use the canonical
sample feed as your primary fixture (feed it via `strings.NewReader` to prove
streaming): a `#` comment, a genuine blank line, then valid and malformed records
— asserting the exact quotes and the exact `(line, kind)` errors. Then:

```
cd go-katas/practice && go test -race ./feedparser/
```

## Reference

Worked solution: `go-katas/solution/feedparser/`.

Extension: add a streaming **transform** — a `func Filter(r io.Reader, w io.Writer, keep func(Quote) bool) error` that reads the feed and writes back only the records whose quote passes `keep`, still one line at a time, so a multi-gigabyte feed can be filtered with flat memory. Then benchmark it against a slurp-and-filter version to see the allocation difference.
