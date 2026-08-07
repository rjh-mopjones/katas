package org.kata.arbitrage;

import java.math.BigDecimal;

/**
 * A single bookmaker's decimal price for one selection in a market.
 *
 * <p>Decimal (European) odds: a stake of 1 pays back {@code odds} if the selection wins, so the
 * implied probability the book assigns to that outcome is {@code 1/odds}. Odds must be strictly
 * greater than 1 — odds of exactly 1 imply a certain outcome with zero return, and odds at or
 * below 1 are not a coherent price for a stake.
 */
public record Quote(String book, String selection, BigDecimal odds) {
    public Quote {
        if (book == null || book.isBlank()) {
            throw new IllegalArgumentException("book is required");
        }
        if (selection == null || selection.isBlank()) {
            throw new IllegalArgumentException("selection is required");
        }
        if (odds == null || odds.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("odds must be > 1: " + odds);
        }
    }
}
