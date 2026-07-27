package org.kata.feedparser;

import java.util.List;
import java.util.stream.Stream;

public final class FeedParser {

    public static Stream<ParsedLine> parse(Stream<String> lines) {
        throw new UnsupportedOperationException();
    }

    public static ParseSummary parseAll(Stream<String> lines) {
        throw new UnsupportedOperationException();
    }

    public record ParseSummary(List<Quote> quotes, List<ParseError> errors) {
    }
}
