package org.kata.blotter;

/**
 * Which side of the market a {@link Trade} was executed on.
 *
 * <p>Modelled as an enum rather than a boolean so call sites are self-documenting
 * (no "true means buy" footgun) and exhaustiveness is checkable in switch expressions.
 */
public enum Side {
    BUY,
    SELL
}
