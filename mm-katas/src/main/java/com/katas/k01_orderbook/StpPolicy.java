package com.katas.k01_orderbook;

/**
 * Self-trade-prevention policy: what the book does when an incoming order would match a resting order
 * from the <em>same account</em>.
 */
public enum StpPolicy {
    /** No prevention — same-account self-matches are allowed to trade. */
    NONE,
    /** Cancel the incoming (newest) order's unmatched remainder instead of self-matching. */
    CANCEL_NEWEST,
    /** Cancel the resting order and continue matching the incoming order against the rest of the book. */
    CANCEL_RESTING
}
