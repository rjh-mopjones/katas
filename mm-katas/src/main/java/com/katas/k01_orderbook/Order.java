package com.katas.k01_orderbook;

/**
 * An order submitted to the book.
 *
 * <p>Prices are integer <em>ticks</em> — never use floating point for price comparison. For a
 * {@link OrderType#MARKET} order the {@link #price()} field is ignored.
 *
 * @param id      unique order id (used for cancellation and to tag fills)
 * @param account owning account (used for self-trade prevention)
 * @param side    buy or sell
 * @param type    limit or market
 * @param price   limit price in ticks (ignored for market orders)
 * @param qty     order quantity; must be positive
 */
public record Order(long id, String account, Side side, OrderType type, long price, long qty) {

    public Order {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive: " + qty);
        }
    }

    /** A limit order resting at {@code price} ticks. */
    public static Order limit(long id, String account, Side side, long price, long qty) {
        return new Order(id, account, side, OrderType.LIMIT, price, qty);
    }

    /** A market order that executes against the best available prices. */
    public static Order market(long id, String account, Side side, long qty) {
        return new Order(id, account, side, OrderType.MARKET, 0L, qty);
    }
}
