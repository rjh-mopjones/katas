# Blotter

## Approach
Every query is a single `stream().collect(...)` call over the trade list — nothing is mutated, nothing is stateful, and each method returns a fresh `Map` or record.

- `pnlByDeskAndSymbol` nests a second `groupingBy` (keyed by symbol) as the *downstream* collector of the first (keyed by desk), with `Collectors.reducing(BigDecimal.ZERO, Trade::pnl, BigDecimal::add)` as the innermost downstream. `BigDecimal` sums need an explicit identity and combiner — there's no `Collectors.summingBigDecimal` in the JDK, so `reducing` is the idiomatic substitute where precision matters for money.
- `winnersVsLosers` uses `Collectors.partitioningBy` rather than `groupingBy(predicate)` — cheaper and more direct when there are exactly two outcomes, and the result always has both `true`/`false` keys present, even on an empty list.
- `minMaxPnl` finds the min and max pnl in **one pass** with `Collectors.teeing`, fanning the same stream into two downstream collectors (`mapping(pnl, minBy)` and `mapping(pnl, maxBy)`) and merging their `Optional` results into `MinMax`. The naive alternative — separate `.stream().min(...)` and `.stream().max(...)` calls — walks the list twice; invisible on a small test, real on a multi-million-row end-of-day blotter.
- `tagCounts` is a fan-out from "one trade, many tags" to "one entry per tag occurrence": `flatMap` flattens each trade's tag list into a single stream of strings before `groupingBy(identity(), counting())`.

## The real challenge
- **Multi-level `groupingBy`**: nest a second `groupingBy` (keyed by symbol) as the *downstream* collector of the first (keyed by desk), with a `reducing` collector as the innermost downstream to sum `BigDecimal` pnl per bucket.
- **`BigDecimal` sums need an identity and a combiner**: there's no `Collectors.summingBigDecimal` in the JDK — reach for `Collectors.reducing(BigDecimal.ZERO, Trade::pnl, BigDecimal::add)`.
- **One pass, not two**: `minMaxPnl` must not call `.stream().min(...)` and then `.stream().max(...)` separately — that's two passes over the same list. `Collectors.teeing` fans a single stream into two downstream collectors and merges their results.
- **`partitioningBy` always has both keys**: unlike `groupingBy`, the result map has `true` and `false` present even when one side is empty — don't special-case a missing key.
- **`flatMap` before you group**: `tagCounts` is a fan-out from "one trade, many tags" to "one entry per tag occurrence" — flatten the tag lists into a single stream of strings before grouping and counting.

## Common mistakes & senior signal
- Reaching for two separate `stream().min(...)`/`stream().max(...)` calls in `minMaxPnl` — it compiles and passes small tests, but it's a second full pass over the data. Naming `Collectors.teeing` as the single-pass fix is the signal.
- Summing `BigDecimal` with `Collectors.summingDouble`-style thinking (converting to `double` and back) — loses precision on money. Knowing `reducing(BigDecimal.ZERO, ..., BigDecimal::add)` is the idiomatic substitute shows awareness that there's no built-in `summingBigDecimal`.
- Using `groupingBy(predicate)` instead of `partitioningBy` for a two-way split — works, but `partitioningBy` is cheaper and guarantees both `true`/`false` keys exist, avoiding a `NullPointerException` or defensive `getOrDefault` on the caller side.
- Forgetting to `flatMap` before grouping tags — grouping by `Trade::tags` (a `List<String>`) instead of flattening first produces a map keyed by tag *lists*, not individual tags.
- Mutating the input list or caching results across calls — every method should be a pure function of its input; a stateful blotter query is a smell here since the class is explicitly a query surface, not a ledger.

## Extensions
- `summarizingDouble`/`summarizingInt` for count/sum/min/max/average in one collector (works on primitives, not directly on `BigDecimal`).
- A custom `Collector` (via `Collector.of`) for a bespoke accumulator, e.g. VWAP or a running Sharpe ratio, that `reducing` can't express.
- Parallel-stream caveats: `groupingBy`'s default `HashMap` merge and `reducing`'s associative combiner are parallel-safe, but a parallel stream only pays off above a data-size threshold — measure before reaching for `.parallelStream()`.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/blotter/`)
- Java Interview Primer: Q90 (Streams / lazy pipelines), Q94 (`Collector`/reduction)
