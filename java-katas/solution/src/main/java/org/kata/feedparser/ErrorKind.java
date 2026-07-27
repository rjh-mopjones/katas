package org.kata.feedparser;

/**
 * The distinct ways a single feed line can fail to parse into a {@link Quote}.
 *
 * <p>Modelled as an enum rather than free-text messages so downstream code can react
 * exhaustively (a switch over the kinds is checkable) and so tests can assert the <i>precise</i>
 * failure mode of a line rather than string-matching a message. Real feed handlers route different
 * kinds differently: a {@link #WRONG_FIELD_COUNT} usually means a framing/protocol desync worth
 * alerting on, whereas an {@link #INVALID_QTY} is more often a single bad tick to drop and move on.
 *
 * <p>The order of the constants mirrors the <b>validation order</b> the parser applies to a line —
 * field-count, then empty-symbol, then bid, then ask, then qty. Only the <i>first</i> failure on a
 * line is reported: parsing short-circuits, exactly as the constants are listed.
 */
public enum ErrorKind {
    /** The line did not split into exactly four {@code |}-delimited fields. */
    WRONG_FIELD_COUNT,
    /** Field count was correct but the symbol (field 0) was empty after trimming. */
    EMPTY_SYMBOL,
    /** The bid field (field 1) was not a parseable {@code double}. */
    INVALID_BID,
    /** The ask field (field 2) was not a parseable {@code double}. */
    INVALID_ASK,
    /** The qty field (field 3) was not a parseable, non-negative {@code long}. */
    INVALID_QTY
}
