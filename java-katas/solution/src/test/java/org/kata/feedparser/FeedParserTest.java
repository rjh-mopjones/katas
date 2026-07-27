package org.kata.feedparser;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FeedParserTest {

    /** The canonical sample feed shared across all six language ports — its output is the contract. */
    private static Stream<String> canonicalFeed() {
        return Stream.of(
                "# market data feed",     // line 1: comment  → skipped
                "LIV-MUN|1.95|2.05|1000", // line 2: quote
                "",                        // line 3: blank    → skipped
                "ARS-CHE|1.50|1.60|500",  // line 4: quote
                "|1.0|2.0|10",            // line 5: EMPTY_SYMBOL
                "BAD|x|2.0|10",           // line 6: INVALID_BID
                "TOO|1.0|2.0",            // line 7: WRONG_FIELD_COUNT
                "NEG|1.0|2.0|-5"          // line 8: INVALID_QTY
        );
    }

    @Test
    void canonical_feed_yields_exact_quotes() {
        var summary = FeedParser.parseAll(canonicalFeed());
        assertEquals(
                List.of(
                        new Quote("LIV-MUN", 1.95, 2.05, 1000),
                        new Quote("ARS-CHE", 1.50, 1.60, 500)
                ),
                summary.quotes());
    }

    @Test
    void canonical_feed_yields_exact_errors_with_physical_line_numbers() {
        var summary = FeedParser.parseAll(canonicalFeed());
        assertEquals(
                List.of(
                        new ParseError(5, ErrorKind.EMPTY_SYMBOL),
                        new ParseError(6, ErrorKind.INVALID_BID),
                        new ParseError(7, ErrorKind.WRONG_FIELD_COUNT),
                        new ParseError(8, ErrorKind.INVALID_QTY)
                ),
                summary.errors());
    }

    @Test
    void empty_input_yields_no_quotes_and_no_errors() {
        var summary = FeedParser.parseAll(Stream.of());
        assertTrue(summary.quotes().isEmpty());
        assertTrue(summary.errors().isEmpty());
    }

    @Test
    void comment_and_blank_only_feed_yields_nothing() {
        var summary = FeedParser.parseAll(Stream.of("# header", "", "   ", "\t", "# another"));
        assertTrue(summary.quotes().isEmpty());
        assertTrue(summary.errors().isEmpty());
    }

    @Test
    void wrong_field_count_too_few_fields() {
        var summary = FeedParser.parseAll(Stream.of("TOO|1.0|2.0"));
        assertEquals(List.of(new ParseError(1, ErrorKind.WRONG_FIELD_COUNT)), summary.errors());
    }

    @Test
    void wrong_field_count_too_many_fields() {
        // Trailing empty field must be counted (split limit -1) — this is 5 fields, not 4.
        var summary = FeedParser.parseAll(Stream.of("MANY|1.0|2.0|10|"));
        assertEquals(List.of(new ParseError(1, ErrorKind.WRONG_FIELD_COUNT)), summary.errors());
    }

    @Test
    void empty_symbol_is_rejected() {
        var summary = FeedParser.parseAll(Stream.of("|1.0|2.0|10"));
        assertEquals(List.of(new ParseError(1, ErrorKind.EMPTY_SYMBOL)), summary.errors());
    }

    @Test
    void invalid_bid_is_rejected() {
        var summary = FeedParser.parseAll(Stream.of("SYM|x|2.0|10"));
        assertEquals(List.of(new ParseError(1, ErrorKind.INVALID_BID)), summary.errors());
    }

    @Test
    void invalid_ask_is_rejected() {
        var summary = FeedParser.parseAll(Stream.of("SYM|1.0|y|10"));
        assertEquals(List.of(new ParseError(1, ErrorKind.INVALID_ASK)), summary.errors());
    }

    @Test
    void invalid_qty_non_numeric_is_rejected() {
        var summary = FeedParser.parseAll(Stream.of("SYM|1.0|2.0|z"));
        assertEquals(List.of(new ParseError(1, ErrorKind.INVALID_QTY)), summary.errors());
    }

    @Test
    void invalid_qty_negative_is_rejected() {
        var summary = FeedParser.parseAll(Stream.of("SYM|1.0|2.0|-5"));
        assertEquals(List.of(new ParseError(1, ErrorKind.INVALID_QTY)), summary.errors());
    }

    @Test
    void validation_order_reports_only_the_first_failure() {
        // Every field is bad: field count is fine (4), symbol empty, bid non-numeric, qty negative.
        // The first failing check in order (empty-symbol) must win.
        var summary = FeedParser.parseAll(Stream.of("|x|y|-5"));
        assertEquals(List.of(new ParseError(1, ErrorKind.EMPTY_SYMBOL)), summary.errors());
    }

    @Test
    void bid_is_checked_before_ask() {
        // Both bid and ask are non-numeric → bid wins (checked first).
        var summary = FeedParser.parseAll(Stream.of("SYM|x|y|10"));
        assertEquals(List.of(new ParseError(1, ErrorKind.INVALID_BID)), summary.errors());
    }

    @Test
    void zero_qty_is_valid() {
        var summary = FeedParser.parseAll(Stream.of("SYM|1.0|2.0|0"));
        assertEquals(List.of(new Quote("SYM", 1.0, 2.0, 0)), summary.quotes());
        assertTrue(summary.errors().isEmpty());
    }

    @Test
    void surrounding_whitespace_on_a_record_line_is_trimmed_before_parsing() {
        var summary = FeedParser.parseAll(Stream.of("   LIV-MUN|1.95|2.05|1000\t"));
        assertEquals(List.of(new Quote("LIV-MUN", 1.95, 2.05, 1000)), summary.quotes());
        assertTrue(summary.errors().isEmpty());
    }

    @Test
    void parse_stream_produces_ok_and_err_arms_with_line_numbers() {
        // Drive the lazy Stream<ParsedLine> directly and pin the sealed-type outcomes.
        List<ParsedLine> parsed = FeedParser.parse(canonicalFeed()).toList();
        assertEquals(6, parsed.size()); // 2 comment/blank skipped, 6 emitted

        assertEquals(new ParsedLine.Ok(2, new Quote("LIV-MUN", 1.95, 2.05, 1000)), parsed.get(0));
        assertEquals(new ParsedLine.Ok(4, new Quote("ARS-CHE", 1.50, 1.60, 500)), parsed.get(1));
        assertEquals(new ParsedLine.Err(new ParseError(5, ErrorKind.EMPTY_SYMBOL)), parsed.get(2));
        assertEquals(new ParsedLine.Err(new ParseError(8, ErrorKind.INVALID_QTY)), parsed.get(5));
    }

    @Test
    void parse_is_lazy_and_supports_short_circuiting() {
        // A short-circuiting terminal must not force the whole stream. Prove laziness by proving
        // findFirst stops early: an infinite stream of valid records still terminates.
        Quote first = FeedParser.parse(Stream.generate(() -> "SYM|1.0|2.0|1"))
                .map(p -> ((ParsedLine.Ok) p).quote())
                .findFirst()
                .orElseThrow();
        assertEquals(new Quote("SYM", 1.0, 2.0, 1), first);
    }
}
