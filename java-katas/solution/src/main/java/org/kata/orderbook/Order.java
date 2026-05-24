package org.kata.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable limit order resting on, or being submitted to, the book.
 *
 * <p>{@code price} uses {@link BigDecimal} rather than {@code double} — money never uses
 * binary floating point. Rounding errors in a matching engine compound into real P&L.
 *
 * <p>{@code submittedAt} is the tiebreaker for <b>price-time priority</b>: at the same
 * price level, the order that arrived first matches first (FIFO). Real exchanges
 * timestamp to microsecond or nanosecond precision precisely to make this ordering
 * deterministic.
 *
 * <p>Records give us value-equality, a free constructor, and immutability. Immutability
 * matters here because resting orders are referenced from multiple structures
 * (price-level deques and the flat id index) and a partial fill must not mutate them
 * in place — see {@link #withQty(int)}.
 */
public record Order(UUID id, Side side, BigDecimal price, int qty, Instant submittedAt) {
    public Order {
        // Compact-constructor invariants. A non-positive price or qty has no economic
        // meaning and is rejected at the boundary rather than poisoning the book.
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("price must be positive");
        if (qty <= 0) throw new IllegalArgumentException("qty must be positive");
    }

    /**
     * Returns a copy with a new quantity, preserving id/side/price/timestamp.
     *
     * <p>Used when an incoming aggressor partially consumes a resting order: the
     * remaining qty stays at the front of the FIFO queue (it keeps its original
     * time priority — only the qty shrinks). Also used to rest the residual of an
     * aggressor that wasn't fully filled on entry.
     */
    public Order withQty(int newQty) {
        return new Order(id, side, price, newQty, submittedAt);
    }
}
