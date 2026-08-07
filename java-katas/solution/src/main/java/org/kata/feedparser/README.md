# Market-Data Feed Parser

## Approach
`parse(Stream<String>)` maps lines to `ParsedLine` outcomes without ever materialising the input or
output into a `List` — that's what lets a caller call `findFirst()`, `limit(n)`, or drain an
effectively-unbounded live-socket stream through the same code path. Laziness is achieved with
`Stream.mapMulti`, which lets the mapping step emit zero-or-one `ParsedLine` per input line: skipped
blank/comment lines emit nothing, every other line emits exactly one `Ok` or `Err`.

Since `Stream` carries no built-in element index, the 1-based physical line number is threaded through
by hand with an `AtomicInteger` incremented once per input line, inside the mapping step, before the
skip check. That ordering is what makes a skipped line still "count" — the counter advances even
though nothing is emitted for it — so a later error reports the line it actually appears on in the raw
file. This is only correct because the pipeline is ordered and sequential (one increment per element
in encounter order); the class deliberately never calls `parallel()`.

Each line's outcome is modeled as a value, not an exception: the sealed `ParsedLine` (`Ok` holding a
`Quote`, `Err` holding a `ParseError`) is consumed with an exhaustive `switch`, so the compiler — not a
missed `if` branch — guarantees every outcome is handled. Validation runs in a fixed order and returns
on the first failure (`WRONG_FIELD_COUNT` → `EMPTY_SYMBOL` → `INVALID_BID` → `INVALID_ASK` →
`INVALID_QTY`), using `split("\\|", -1)` (limit `-1`) so trailing empty fields survive instead of being
silently dropped by the default `split` behaviour.

## The real challenge
- **Keep it lazy.** `Stream` has no element index, so thread the 1-based physical line number yourself — an `AtomicInteger` incremented once per input line inside the mapping step. This is only correct on an *ordered, sequential* pipeline: one increment per element in encounter order. Never `parallel()` this stream.
- **Skip without losing position.** Blank/`#` lines emit *nothing* yet must still bump the counter, so a later error keeps its true physical line. `mapMulti` (emit zero-or-one per input) is a clean lazy way to drop the skipped lines; a `map`-then-`filter` of an `Optional` also works.
- **`split("\\|", -1)` — the trailing-empty gotcha.** The default `split("\\|")` (limit 0) *discards trailing empty fields*: `"TOO|1.0|2.0|".split("\\|")` is length 3, silently swallowing a malformed trailing field. Use limit **-1** so the field count is exact and empty fields (including an empty symbol) survive. Escape the pipe (`"\\|"`) — a bare `|` is regex alternation.
- **Errors are values.** Model each outcome as the sealed `ParsedLine` (`Ok` / `Err`) and handle both arms with an exhaustive `switch` — the compiler rejects a forgotten arm, so no outcome is ever dropped.
- **Non-negative qty.** `Long.parseLong("-5")` succeeds and returns `-5`; the non-negative rule is a separate explicit guard, not a parse failure.

## Common mistakes & senior signal
- Materialising into a `List<String>` first (e.g. `lines.collect(toList())`) before processing —
  defeats the whole point of a lazy pipeline and breaks on an unbounded source. The signal is keeping
  everything as a `Stream` end-to-end.
- Using the default `split("\\|")` instead of the two-arg `-1` limit form — passes on inputs without
  trailing empty fields, then fails mysteriously on `"TOO|1.0|2.0|"` style inputs where a trailing
  empty field should count toward the field total.
- Incrementing the line counter *after* the skip check (or only for non-skipped lines) — silently
  shifts every subsequent error's reported line number once a blank/comment line appears earlier in
  the feed.
- Reaching for exceptions to signal a bad line instead of the sealed `ParsedLine` — throws abort the
  whole stream and lose every good tick behind the bad one, which directly violates the "never lose
  good ticks" requirement.
- Forgetting the separate non-negative guard on `qty` — `Long.parseLong` happily parses `"-5"`, so
  relying on the parse alone silently accepts negative quantities.

## Extensions
- Support a quoted/escaped `|` inside a symbol.
- Emit a running `Stream<ParseSummary>` snapshot instead of (or alongside) `parseAll`'s one-shot drain.
- Add an `Iterator`/`BufferedReader.lines()` source adapter for a live socket, proving the pipeline
  really is source-agnostic.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/feedparser/`)
- Java Interview Primer: Q90 (Streams / lazy pipelines), Q94 (`Collector`/reduction), sealed types & pattern matching (`switch` on sealed interfaces)
