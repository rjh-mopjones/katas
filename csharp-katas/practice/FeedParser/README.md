# FeedParser

Streaming, allocation-conscious parser for a pipe-delimited market-data feed, built on
`ReadOnlySpan<char>` and lazy `yield return`.

## The problem

A market-data feed arrives as text, one record per line: `SYMBOL|BID|ASK|QTY`. Parse it into
`Quote` values while streaming (one line pulled at a time, O(1) memory) and reporting every
malformed line — tagged with its physical line number — instead of throwing.

## Requirements

- After trimming, a **blank line** or a line **starting with `#`** is **skipped** — not a record,
  not an error. It still counts toward the **1-based physical line number**.
- A record must have **exactly four** `|`-separated fields. Validate in this fixed order, reporting
  the **first** failure only:
  1. not four fields → `WrongFieldCount`
  2. empty symbol → `EmptySymbol`
  3. bid not a `double` → `InvalidBid`
  4. ask not a `double` → `InvalidAsk`
  5. qty not a **non-negative integer** → `InvalidQty`
- Every `ParseError` carries the 1-based physical line number.
- `Parse` streams lazily (one `ParsedLine` per non-skipped line); `ParseAll` partitions into quotes
  and errors.

### Canonical sample feed (the output is the contract)

```
# market data feed
LIV-MUN|1.95|2.05|1000

ARS-CHE|1.50|1.60|500
|1.0|2.0|10
BAD|x|2.0|10
TOO|1.0|2.0
NEG|1.0|2.0|-5
```

- quotes = `[Quote("LIV-MUN",1.95,2.05,1000), Quote("ARS-CHE",1.50,1.60,500)]`
- errors = `[(5,EmptySymbol), (6,InvalidBid), (7,WrongFieldCount), (8,InvalidQty)]`

## What you implement

```csharp
public enum ErrorKind { WrongFieldCount, EmptySymbol, InvalidBid, InvalidAsk, InvalidQty }
public readonly record struct Quote(string Symbol, double Bid, double Ask, ulong Qty);
public readonly record struct ParseError(int Line, ErrorKind Kind);
public readonly record struct ParsedLine(int Line, Quote? Quote, ParseError? Error);

public static class FeedParser
{
    public static IEnumerable<ParsedLine> Parse(IEnumerable<string> lines);
    public static (IReadOnlyList<Quote> Quotes, IReadOnlyList<ParseError> Errors) ParseAll(IEnumerable<string> lines);
}
```

## The real challenge

- **Zero-allocation field parsing.** Slice the four fields off a `ReadOnlySpan<char>` without
  cutting substrings — use `MemoryExtensions.Split` into a `stackalloc Span<Range>` (a buffer of 5
  ranges distinguishes "exactly 4" from "5+"), then parse numbers straight off the span with the
  span overloads of `double.TryParse` / `ulong.TryParse`. Only the symbol allocates a `string`
  (`.ToString()`), at the very end.
- **`TryParse` over exceptions** on the hot path; a leading `-` makes `ulong.TryParse` return
  `false`, which *is* the non-negative rule.
- **Streaming.** `Parse` is a `yield return` iterator. A `ref struct` span cannot live across a
  `yield`, so isolate the per-line span work in a plain helper method that returns a value type.
- **Invariant culture** for every number — a feed uses `'.'` regardless of host locale.

## Run

Write your own tests under `practice.tests/FeedParser/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~FeedParser"
```

## Reference

`solution/FeedParser/` — see `FeedParser.cs` for the reference implementation.

Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/api/system.readonlyspan-1
and span-based parsing: https://learn.microsoft.com/en-us/dotnet/api/system.double.tryparse#system-double-tryparse(system-readonlyspan((system-char))-system-globalization-numberstyles-system-iformatprovider-system-double@)
