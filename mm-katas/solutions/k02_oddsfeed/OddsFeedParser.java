package com.katas.k02_oddsfeed;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Worked reference — passes Stage 1 through Stage 3 (Stage 4 is {@link ConflatingBuffer}). See
 * NOTES.md for the pivot each stage forces.
 *
 * <p>Design: an append-only {@link StringBuilder} buffer flushed only on {@code '\n'} (so a line
 * split across {@code feed()} calls reassembles once); a running {@code lineNumber}; and a
 * {@link Map} of expected-next seq per book that resequences the stream (deliver / report a gap /
 * drop stale duplicates).
 */
public final class OddsFeedParser {

    private final FeedListener listener;
    private final StringBuilder buffer = new StringBuilder();
    private final Map<String, Long> expectedByBook = new HashMap<>();
    private long lineNumber = 0;

    public OddsFeedParser(FeedListener listener) {
        this.listener = listener;
    }

    public void feed(String chunk) {
        buffer.append(chunk);
        int start = 0;
        for (int i = 0; i < buffer.length(); i++) {
            if (buffer.charAt(i) == '\n') {
                processLine(buffer.substring(start, i)); // '\r' (CRLF) is removed by strip() below
                start = i + 1;
            }
        }
        buffer.delete(0, start); // keep only the incomplete trailing remainder
    }

    /** Parse and dispatch one complete line (its terminator already stripped). */
    private void processLine(String rawLine) {
        lineNumber++;
        String line = rawLine.strip(); // drops CR, and surrounding/extra whitespace
        if (line.isEmpty()) {
            return; // a blank line is skipped, not an error
        }

        String[] f = line.split("\\|", -1); // -1 keeps trailing empties: a trailing '|' → 7 fields
        if (f.length != 6) {
            error(rawLine, "wrong field count: expected 6, got " + f.length);
            return;
        }

        long seq;
        try {
            seq = Long.parseLong(f[0].strip());
        } catch (NumberFormatException e) {
            error(rawLine, "bad seq: '" + f[0].strip() + "'");
            return;
        }
        BigDecimal odds;
        try {
            odds = new BigDecimal(f[5].strip());
        } catch (NumberFormatException e) {
            error(rawLine, "bad odds: '" + f[5].strip() + "'");
            return;
        }

        OddsUpdate update =
                new OddsUpdate(seq, f[1].strip(), f[2].strip(), f[3].strip(), f[4].strip(), odds);
        resequenceAndDispatch(update);
    }

    /** Apply per-book gap/duplicate logic, then deliver in-sequence updates. */
    private void resequenceAndDispatch(OddsUpdate u) {
        Long expectedBoxed = expectedByBook.get(u.book());
        long expected = (expectedBoxed == null) ? u.seq() : expectedBoxed; // first seen → expect it

        if (u.seq() < expected) {
            return; // <= lastDelivered: a duplicate or an old/replayed line → drop
        }
        if (u.seq() > expected) {
            listener.onGap(u.book(), expected, u.seq()); // report the hole once
        }
        listener.onUpdate(u);
        expectedByBook.put(u.book(), u.seq() + 1); // note: overflows at Long.MAX_VALUE (acceptable)
    }

    private void error(String rawLine, String reason) {
        listener.onError(new ParseError(lineNumber, rawLine, reason));
    }
}
