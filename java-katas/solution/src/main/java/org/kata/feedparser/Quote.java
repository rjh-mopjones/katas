package org.kata.feedparser;

/**
 * An immutable, typed market-data quote parsed from one feed line.
 *
 * <p>A quote is the tradable snapshot for a symbol: the best {@code bid} (the price a buyer will
 * pay), the best {@code ask} (the price a seller will accept), and the {@code qty} available. The
 * feed delivers these as raw text — {@code SYMBOL|BID|ASK|QTY} — and this record is what a valid
 * line becomes once the four fields have been validated and coerced to their proper types.
 *
 * <p>{@code bid}/{@code ask} are {@code double} here deliberately: this kata is about the parsing
 * pipeline, not P&amp;L arithmetic. A production pricing engine would use {@code BigDecimal} (see the
 * {@code orderbook} kata) so rounding never accumulates into real money errors — but that would
 * obscure the point, which is the lazy {@code Stream} mapping and the line-numbered error handling.
 *
 * <p>Modelled as a {@code record} for value-equality (so tests can assert on whole quotes),
 * immutability (a parsed quote is a fact about a point in the feed and must not drift), and a free
 * canonical constructor.
 */
public record Quote(String symbol, double bid, double ask, long qty) {
}
