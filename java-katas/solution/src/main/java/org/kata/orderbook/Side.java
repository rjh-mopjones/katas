package org.kata.orderbook;

/**
 * Side of a limit order. Two-valued because this is a continuous double-auction book —
 * BUY orders (bids) sit on one side, SELL orders (asks/offers) on the other, and an
 * incoming aggressor matches against the opposite side.
 *
 * <p>Modelled as an enum rather than a boolean so call sites are self-documenting
 * (no "true means buy" footgun) and exhaustiveness is checkable in switch expressions.
 */
public enum Side { BUY, SELL }
