package com.katas.k01_orderbook;

import java.util.List;
import java.util.OptionalLong;

/**
 * A price-time-priority limit order book for a single instrument.
 *
 * <p>Prices are integer ticks. Among orders at the same price, the earliest-submitted trades first
 * (FIFO). The best bid is the highest resting buy price; the best ask is the lowest resting sell
 * price.
 *
 * <p>TODO: implement every method. They all throw until you do.
 */
public final class OrderBook {

    /** Create a book with no self-trade prevention. */
    public OrderBook() {
        this(StpPolicy.NONE);
    }

    /** Create a book with the given self-trade-prevention policy. */
    public OrderBook(StpPolicy stpPolicy) {
        // TODO: keep whatever state you need.
    }

    /**
     * Submit an order. Any immediately-executable quantity trades against resting orders on the
     * opposite side — best price first, then FIFO within a price level — and the resulting
     * {@link Fill}s are returned in execution order. A limit order's unmatched remainder rests in the
     * book; a market order's unmatched remainder is discarded.
     *
     * @return the fills produced, in execution order (empty if the order did not trade)
     */
    public List<Fill> submit(Order order) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Cancel a resting order by id.
     *
     * @return {@code true} if an open order with that id was resting and has now been removed;
     *         {@code false} if no such order is resting (unknown or already fully filled/cancelled)
     */
    public boolean cancel(long orderId) {
        throw new UnsupportedOperationException("TODO");
    }

    /** The best (highest) resting bid price, or empty if there are no bids. */
    public OptionalLong bestBid() {
        throw new UnsupportedOperationException("TODO");
    }

    /** The best (lowest) resting ask price, or empty if there are no asks. */
    public OptionalLong bestAsk() {
        throw new UnsupportedOperationException("TODO");
    }
}
