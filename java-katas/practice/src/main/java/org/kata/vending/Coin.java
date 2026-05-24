package org.kata.vending;

import java.math.BigDecimal;

/**
 * Coin denominations the machine understands.
 *
 * <p>Money is modelled as {@link BigDecimal} throughout — never {@code double}. Binary floating
 * point cannot represent decimal fractions like 0.10 exactly, so accumulating "ten dimes" with
 * {@code double} drifts off 1.00 and corrupts equality checks. {@code BigDecimal} is exact and
 * lets us pin a scale (2 dp) and rounding mode (HALF_EVEN / banker's rounding) at the boundary.
 *
 * <p>The denominations here happen to be <em>canonical</em> — every amount {@code >= 0} can be
 * made greedily (largest-first) with the minimum number of coins. That is what licences the
 * simple greedy algorithm in {@code VendingMachine#planGreedyChange}. Drop in a non-canonical
 * set (e.g. 1, 3, 4) and greedy breaks; you'd need a DP min-coin solution instead.
 */
public enum Coin {
    PENNY(new BigDecimal("0.01")),
    NICKEL(new BigDecimal("0.05")),
    DIME(new BigDecimal("0.10")),
    QUARTER(new BigDecimal("0.25")),
    DOLLAR(new BigDecimal("1.00"));

    private final BigDecimal value;

    Coin(BigDecimal value) { this.value = value; }
    public BigDecimal value() { return value; }
}
