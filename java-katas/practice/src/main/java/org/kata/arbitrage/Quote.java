package org.kata.arbitrage;

import java.math.BigDecimal;

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
