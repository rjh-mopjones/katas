package org.kata.positionkeeper;

import java.math.BigDecimal;

/**
 * A single matched bet on a market selection at decimal odds.
 *
 * <p>{@code odds} is expressed in <b>decimal (European) odds</b> — the total return per unit
 * stake, including the stake itself — so profit on a winning back bet is {@code stake * (odds -
 * 1)}. Decimal odds must be strictly greater than {@code 1}; odds of exactly {@code 1} return
 * only the stake, which is not an economically meaningful bet for a book to accept.
 *
 * <p>{@code stake} and {@code odds} are {@link BigDecimal} — money and prices never use binary
 * floating point; every comparison in this module uses {@code compareTo}, never {@code ==}.
 */
public record Bet(String market, String selection, Side side, BigDecimal stake, BigDecimal odds) {
    public Bet {
        if (market == null || market.isBlank()) {
            throw new IllegalArgumentException("market must not be blank");
        }
        if (selection == null || selection.isBlank()) {
            throw new IllegalArgumentException("selection must not be blank");
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (stake == null || stake.signum() <= 0) {
            throw new IllegalArgumentException("stake must be positive");
        }
        if (odds == null || odds.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("odds must be greater than 1");
        }
    }
}
