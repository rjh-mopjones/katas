package com.katas.k01_orderbook;

/**
 * A trade between a resting (maker) order and an incoming (taker) order. Trades execute at the
 * <em>resting</em> order's price.
 *
 * @param makerOrderId the resting order that was hit
 * @param takerOrderId the incoming order that crossed
 * @param price        execution price in ticks (the maker's price)
 * @param qty          quantity traded
 */
public record Fill(long makerOrderId, long takerOrderId, long price, long qty) {
}
