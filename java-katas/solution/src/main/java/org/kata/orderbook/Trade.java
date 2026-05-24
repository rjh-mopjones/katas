package org.kata.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable execution report: two orders crossed at a price for a quantity.
 *
 * <p>The buy/sell ids are split out (rather than carrying full Order references) because
 * the trade is a fact about an event in time, not a pointer to mutable state. Downstream
 * systems (clearing, risk, market data) consume this shape and join back to order
 * identifiers as needed.
 *
 * <p>{@code price} is the resting order's price, not the aggressor's — this is
 * <b>price improvement</b>: the aggressor offered to pay up to (BUY) or accept down to
 * (SELL) their limit but actually transacts at the better resting price.
 *
 * <p>{@code at} is captured once per submission, outside the lock, so all fills produced
 * by a single aggressing order share an identical timestamp — consistent with how
 * exchanges stamp executions to the submission moment.
 */
public record Trade(UUID buyOrderId, UUID sellOrderId, int qty, BigDecimal price, Instant at) {}
