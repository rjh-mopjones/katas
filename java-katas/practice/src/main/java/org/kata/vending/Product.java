package org.kata.vending;

import java.math.BigDecimal;

/**
 * Immutable product descriptor keyed by {@code code} (e.g. {@code "A1"}).
 *
 * <p>A {@code record} gives us value semantics, structural equality and a generated constructor
 * for free. The compact constructor below validates invariants up front so a {@code Product}
 * instance is always well-formed — fail-fast at the boundary rather than defending downstream.
 *
 * <p>Price is {@link BigDecimal} for the same reason coins are: exact decimal arithmetic and a
 * single canonical scale across the domain. Compare prices with {@code compareTo}, not
 * {@code equals} (the latter treats 1.0 and 1.00 as different).
 */
public record Product(String code, String name, BigDecimal price) {
    public Product {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code required");
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("price must be positive");
    }
}
