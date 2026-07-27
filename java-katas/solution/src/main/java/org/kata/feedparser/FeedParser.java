package org.kata.feedparser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Parses a streaming market-data feed of pipe-delimited records into typed {@link Quote}s, lazily,
 * reporting each malformed line with its physical position rather than throwing.
 *
 * <h2>The feed format</h2>
 * One record per line: {@code SYMBOL|BID|ASK|QTY}. Two line kinds are <b>skipped</b> (they are not
 * records and not errors): after trimming, a <b>blank</b> line, or one that <b>starts with
 * {@code #}</b> (a comment). Skipped lines still advance the physical line counter — see below.
 *
 * <h2>Why lazy</h2>
 * The public {@link #parse(Stream)} maps an input {@code Stream<String>} of lines to a
 * {@code Stream<ParsedLine>} without materialising either. That matters for a real feed: it may be
 * enormous (a day's ticks) or effectively unbounded (a live socket adapted to a {@code Stream}), so
 * buffering the whole thing into a {@code List} first is a non-starter. A lazy pipeline processes one
 * line at a time and lets the terminal operation decide how much to pull — {@code findFirst},
 * {@code limit}, a short-circuiting search, or a full drain all work without changing the parser.
 *
 * <h2>Threading the physical line number</h2>
 * Every {@link ParseError} carries the <b>1-based physical line number</b> — counting <i>every</i>
 * input line, including the blank/comment lines that are themselves skipped. An operator diagnosing
 * a bad tick reads the raw file; "line 6" must mean the sixth line of that file, not the sixth
 * successfully-parsed record. Because {@link Stream} has no built-in element index, we thread the
 * counter through with an {@link AtomicInteger} incremented once per input line inside the mapping
 * step. This stays correct under a lazy, ordered, sequential pipeline (one increment per element, in
 * encounter order); it would <i>not</i> be safe to parallelise, which is exactly why {@link #parse}
 * returns an ordered sequential stream and never calls {@code parallel()}.
 *
 * <h2>Per-line outcome as a result type</h2>
 * Each non-skipped line becomes a {@link ParsedLine}: {@link ParsedLine.Ok} (a {@link Quote} plus its
 * line) or {@link ParsedLine.Err} (a {@link ParseError}). Errors are in-band values, not exceptions —
 * a single malformed line must never abort the stream and lose the good ticks behind it. The sealed
 * {@code ParsedLine} lets callers handle both arms exhaustively (the compiler enforces it).
 *
 * <h2>Validation order (short-circuits on first failure)</h2>
 * A line is validated in a fixed order, reporting only the first problem:
 * <ol>
 *   <li>exactly four fields → else {@link ErrorKind#WRONG_FIELD_COUNT};</li>
 *   <li>non-empty symbol → else {@link ErrorKind#EMPTY_SYMBOL};</li>
 *   <li>bid parses as {@code double} → else {@link ErrorKind#INVALID_BID};</li>
 *   <li>ask parses as {@code double} → else {@link ErrorKind#INVALID_ASK};</li>
 *   <li>qty parses as a <b>non-negative</b> {@code long} → else {@link ErrorKind#INVALID_QTY}.</li>
 * </ol>
 *
 * <h2>The {@code split("\\|", -1)} gotcha</h2>
 * Field-splitting uses {@link String#split(String, int)} with a limit of {@code -1}. The default
 * {@code split(regex)} (limit 0) <b>discards trailing empty strings</b>: {@code "TOO|1.0|2.0|".split("\\|")}
 * yields {@code ["TOO","1.0","2.0"]} — length 3, silently swallowing a genuinely malformed trailing
 * field and mis-reporting it as {@code WRONG_FIELD_COUNT} for the wrong reason (or worse, matching 4
 * by accident on other inputs). Limit {@code -1} keeps every empty field, so the field count is exact
 * and empty fields are surfaced (e.g. an empty symbol is preserved rather than dropped). The
 * {@code |} is also regex-escaped ({@code "\\|"}) because a bare {@code |} is regex alternation.
 */
public final class FeedParser {

    private FeedParser() {
    }

    /**
     * Lazily maps a stream of raw feed lines to a stream of per-line outcomes.
     *
     * <p>Blank and {@code #}-comment lines (after trimming) produce <i>nothing</i> — they are
     * filtered out via {@code mapMulti}, which emits zero-or-one {@link ParsedLine} per input line
     * while keeping the pipeline lazy. Each surviving line becomes exactly one {@link ParsedLine.Ok}
     * or {@link ParsedLine.Err}. The returned stream is ordered and sequential; the physical line
     * counter it threads is only correct under that guarantee.
     */
    public static Stream<ParsedLine> parse(Stream<String> lines) {
        // One increment per input line, in encounter order. Correct only because the pipeline is
        // ordered + sequential (see class Javadoc) — do not parallelise this stream.
        AtomicInteger lineNo = new AtomicInteger(0);
        return lines.<ParsedLine>mapMulti((raw, downstream) -> {
            int line = lineNo.incrementAndGet();
            String trimmed = raw.trim();
            // Skip rule: blank or comment lines are neither records nor errors — emit nothing,
            // but the counter has already advanced so later errors keep their physical position.
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return;
            downstream.accept(parseLine(trimmed, line));
        });
    }

    /**
     * Eagerly drains the feed, partitioning outcomes into the list of good {@link Quote}s and the
     * list of {@link ParseError}s (both in feed order).
     *
     * <p>A convenience terminal for the common "give me everything" case, built on the lazy
     * {@link #parse(Stream)} so there is a single source of truth for the parsing rules.
     */
    public static ParseSummary parseAll(Stream<String> lines) {
        List<Quote> quotes = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        parse(lines).forEach(parsed -> {
            // Exhaustive over the sealed ParsedLine — the compiler rejects a missing arm.
            switch (parsed) {
                case ParsedLine.Ok ok -> quotes.add(ok.quote());
                case ParsedLine.Err err -> errors.add(err.error());
            }
        });
        return new ParseSummary(quotes, errors);
    }

    /**
     * Validates and coerces one already-trimmed, non-skipped line into a {@link ParsedLine},
     * applying the fixed validation order and short-circuiting on the first failure.
     */
    private static ParsedLine parseLine(String trimmed, int line) {
        // Limit -1 keeps trailing empty fields so the count and empty-symbol checks are exact.
        String[] fields = trimmed.split("\\|", -1);
        if (fields.length != 4) return err(line, ErrorKind.WRONG_FIELD_COUNT);

        String symbol = fields[0];
        if (symbol.isEmpty()) return err(line, ErrorKind.EMPTY_SYMBOL);

        double bid;
        try {
            bid = Double.parseDouble(fields[1]);
        } catch (NumberFormatException e) {
            return err(line, ErrorKind.INVALID_BID);
        }

        double ask;
        try {
            ask = Double.parseDouble(fields[2]);
        } catch (NumberFormatException e) {
            return err(line, ErrorKind.INVALID_ASK);
        }

        long qty;
        try {
            qty = Long.parseLong(fields[3]);
        } catch (NumberFormatException e) {
            return err(line, ErrorKind.INVALID_QTY);
        }
        // Qty is a size; a negative size is meaningless. Long.parseLong happily accepts "-5",
        // so the non-negative constraint is a separate, explicit guard.
        if (qty < 0) return err(line, ErrorKind.INVALID_QTY);

        return new ParsedLine.Ok(line, new Quote(symbol, bid, ask, qty));
    }

    private static ParsedLine err(int line, ErrorKind kind) {
        return new ParsedLine.Err(new ParseError(line, kind));
    }

    /**
     * The result of draining an entire feed: every good {@link Quote} and every {@link ParseError},
     * each list in feed order. A {@code record} for value-equality so a test can assert on the whole
     * summary at once.
     */
    public record ParseSummary(List<Quote> quotes, List<ParseError> errors) {
    }
}
