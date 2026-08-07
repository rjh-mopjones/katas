package com.katas.k02_oddsfeed;

/**
 * Sink for everything the parser produces. All three callbacks are invoked on the thread that calls
 * {@code feed()} — a listener that does real work should hand off quickly.
 */
public interface FeedListener {

    /** A well-formed, in-sequence update ready to be consumed. */
    void onUpdate(OddsUpdate update);

    /** A malformed line was encountered; it is reported here, never thrown. */
    void onError(ParseError error);

    /**
     * A per-bookmaker sequence gap was detected: updates {@code [expectedSeq, receivedSeq)} were
     * never seen. The {@code receivedSeq} update is still delivered via {@link #onUpdate}.
     */
    void onGap(String book, long expectedSeq, long receivedSeq);
}
