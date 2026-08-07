package org.kata.blotter;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only reporting queries over a trading blotter (a {@link List} of {@link Trade}), written as a
 * tour of {@link Collectors}: multi-level {@code groupingBy}, {@code partitioningBy}, single-pass
 * {@code teeing}, and tag {@code flatMap}/{@code counting}.
 *
 * <h2>Model</h2>
 * Every method takes the trade list as a plain parameter rather than holding state — the blotter here
 * is a query surface, not a stateful ledger (contrast a position keeper, which accumulates). Inputs are
 * never mutated; every result is a fresh {@link Map} or record built by the stream pipeline.
 *
 * <h2>Data structure</h2>
 * All four queries are a single {@code stream().collect(...)} call over {@code trades}:
 * <ul>
 *   <li>{@link #pnlByDeskAndSymbol} nests {@code groupingBy(desk, groupingBy(symbol, reducing(...)))} —
 *       a two-level {@code Map<String, Map<String, BigDecimal>>} built in one pass; the innermost
 *       downstream collector, {@link Collectors#reducing(Object, Function, java.util.function.BinaryOperator)},
 *       sums {@link BigDecimal} pnl per bucket with an explicit {@link BigDecimal#ZERO} identity.</li>
 *   <li>{@link #winnersVsLosers} is a boolean split via {@link Collectors#partitioningBy} — cheaper and
 *       more direct than {@code groupingBy(predicate)} when there are exactly two outcomes, and the
 *       result map always has both {@code true}/{@code false} keys present (an empty list, never a
 *       missing key).</li>
 *   <li>{@link #minMaxPnl} finds the min and max pnl in <b>one pass</b> with {@link Collectors#teeing},
 *       fanning the same stream into two downstream collectors ({@code mapping(pnl, minBy)} and
 *       {@code mapping(pnl, maxBy)}) and merging their {@link java.util.Optional} results into a
 *       {@link MinMax}. The naive alternative — two separate {@code min()}/{@code max()} stream calls —
 *       walks the trade list twice.</li>
 *   <li>{@link #tagCounts} flattens each trade's tag list with {@code flatMap} before grouping+counting —
 *       the many-to-many fan-out from "one trade, many tags" to "one entry per tag occurrence".</li>
 * </ul>
 *
 * <h2>Trade-offs</h2>
 * Collector pipelines read declaratively but hide the iteration count: {@code teeing} is a genuine
 * single pass, while chaining two independent {@code stream().min(...)}/{@code stream().max(...)}
 * calls (a common first draft) is two passes over the same data — invisible in a small test, real on a
 * multi-million-row end-of-day blotter. {@code reducing} with an explicit identity is used over a
 * {@code summingDouble}-style collector because pnl is {@link BigDecimal}: precision matters for money,
 * so there is no {@code Collectors.summingBigDecimal} in the JDK and {@code reducing} is the idiomatic
 * substitute.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li>{@code summarizingDouble}/{@code summarizingInt} for count/sum/min/max/average in one collector
 *       (works on primitives, not directly on {@link BigDecimal}).</li>
 *   <li>A custom {@link java.util.stream.Collector} (via {@code Collector.of}) for a bespoke
 *       accumulator, e.g. VWAP or a running Sharpe ratio, that {@code reducing} can't express.</li>
 *   <li>Parallel-stream caveats: {@code groupingBy}'s default {@link java.util.HashMap} merge and
 *       {@code reducing}'s associative combiner are parallel-safe, but a parallel stream only pays off
 *       above a data-size threshold — measure before reaching for {@code .parallelStream()}.</li>
 * </ul>
 */
public final class Blotter {

    /** One-pass min/max pnl result; both fields are {@code null} when the input is empty. */
    public record MinMax(BigDecimal min, BigDecimal max) {
    }

    /** Sum of pnl per desk, then per symbol within that desk. */
    public Map<String, Map<String, BigDecimal>> pnlByDeskAndSymbol(List<Trade> trades) {
        return trades.stream()
                .collect(Collectors.groupingBy(Trade::desk,
                        Collectors.groupingBy(Trade::symbol,
                                Collectors.reducing(BigDecimal.ZERO, Trade::pnl, BigDecimal::add))));
    }

    /** Trades split into winners ({@code pnl > 0}, key {@code true}) and losers/flat (key {@code false}). */
    public Map<Boolean, List<Trade>> winnersVsLosers(List<Trade> trades) {
        return trades.stream()
                .collect(Collectors.partitioningBy(t -> t.pnl().signum() > 0));
    }

    /** The lowest and highest pnl across all trades, found in one pass over the stream. */
    public MinMax minMaxPnl(List<Trade> trades) {
        return trades.stream()
                .collect(Collectors.teeing(
                        Collectors.mapping(Trade::pnl, Collectors.minBy(Comparator.naturalOrder())),
                        Collectors.mapping(Trade::pnl, Collectors.maxBy(Comparator.naturalOrder())),
                        (min, max) -> new MinMax(min.orElse(null), max.orElse(null))));
    }

    /** How many times each tag occurs across all trades. */
    public Map<String, Long> tagCounts(List<Trade> trades) {
        return trades.stream()
                .flatMap(t -> t.tags().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
