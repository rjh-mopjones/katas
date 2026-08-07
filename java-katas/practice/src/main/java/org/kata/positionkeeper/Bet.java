package org.kata.positionkeeper;

import java.math.BigDecimal;

/**
 * A single matched bet on a market selection at decimal odds.
 *
 * <p>{@code odds} is expressed in decimal (European) odds; {@code stake} and {@code odds} are
 * {@link BigDecimal} — money and prices never use binary floating point.
 */
public record Bet(String market, String selection, Side side, BigDecimal stake, BigDecimal odds) {
}
