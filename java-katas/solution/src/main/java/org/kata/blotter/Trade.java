package org.kata.blotter;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single executed trade line on the blotter: which desk booked it, what instrument, which side,
 * the realised/marked profit-and-loss, and free-form tags (e.g. {@code "algo"}, {@code "manual"},
 * {@code "hedge"}) used for reporting.
 *
 * @param desk   the booking desk, e.g. {@code "rates"} or {@code "equities"}
 * @param symbol the traded instrument
 * @param side   {@link Side#BUY} or {@link Side#SELL}
 * @param pnl    profit (positive) or loss (negative); compare with {@link BigDecimal#compareTo},
 *               never {@code ==} or {@link BigDecimal#equals}
 * @param tags   free-form labels for grouping/reporting; may be empty, never {@code null}
 */
public record Trade(String desk, String symbol, Side side, BigDecimal pnl, List<String> tags) {
}
