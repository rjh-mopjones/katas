package com.katas.k02_oddsfeed;

/**
 * A streaming parser for a bookmaker odds feed.
 *
 * <p>The wire format is one update per line:
 * {@code SEQ|BOOK|EVENT|MARKET|SELECTION|ODDS} — exactly six pipe-separated fields. {@code SEQ}
 * parses to {@code long}, {@code ODDS} to {@link java.math.BigDecimal}. A blank line (after trimming)
 * is skipped, not an error.
 *
 * <p>The parser is fed arbitrary chunks of the byte/character stream: a single line may be split
 * across several {@code feed()} calls, and a single {@code feed()} may carry many lines. Complete
 * lines are parsed and dispatched to the {@link FeedListener}; an incomplete trailing line is held
 * until the rest arrives. Per bookmaker it tracks the expected next sequence number, reporting gaps
 * and dropping stale duplicates so downstream consumers see each update once, in order.
 *
 * <p>TODO: implement {@code feed}. It throws until you do.
 */
public final class OddsFeedParser {

    /** Create a parser that dispatches to {@code listener}. */
    public OddsFeedParser(FeedListener listener) {
        // TODO: keep whatever state you need.
    }

    /**
     * Feed a chunk of the text stream. Complete lines are parsed and dispatched; an incomplete
     * trailing line is buffered until the rest arrives in a later {@code feed()}. Malformed lines are
     * reported via the listener, never thrown, and never desync the buffer.
     */
    public void feed(String chunk) {
        throw new UnsupportedOperationException("TODO");
    }
}
