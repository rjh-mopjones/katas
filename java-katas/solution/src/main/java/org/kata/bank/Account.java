package org.kata.bank;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable account aggregate. Modelled as a {@code record} to make the value-semantics explicit:
 * two {@code Account}s with the same id and balance are interchangeable.
 *
 * <p><b>Why {@link BigDecimal} for balance, never {@code double}.</b> IEEE-754 binary floats
 * cannot exactly represent most decimal fractions ({@code 0.1 + 0.2 != 0.3}). For monetary
 * values this leaks pennies and fails audits. {@code BigDecimal} stores an arbitrary-precision
 * integer with a scale, so {@code 0.10 + 0.20} is exactly {@code 0.30}. Use
 * {@link java.math.RoundingMode#HALF_EVEN} (banker's rounding) for any division/rounding —
 * it is statistically unbiased over many transactions, unlike HALF_UP which drifts upward.
 *
 * <p><b>Why immutable.</b> An immutable {@code Account} is trivially safe to publish across
 * threads and to share between callers without defensive copies. State changes happen by
 * producing a new {@code Account} via {@link #withBalance(BigDecimal)} and atomically
 * swapping the reference in the owning store — the lock protects the swap, not the object.
 */
public record Account(UUID id, BigDecimal balance) {
    // Compact constructor enforces invariants at construction time so no instance can ever
    // exist in an invalid state. This is the record equivalent of a class invariant.
    public Account {
        if (id == null) throw new IllegalArgumentException("id required");
        if (balance == null) throw new IllegalArgumentException("balance required");
        if (balance.signum() < 0) throw new IllegalArgumentException("opening balance must be non-negative");
    }

    /**
     * Returns a new {@code Account} with the same id but the supplied balance. This is the
     * canonical functional-update pattern for immutable value types: callers never mutate
     * an existing account, they replace the reference in the account store. Keeps the
     * record genuinely immutable while still allowing balance evolution over time.
     */
    public Account withBalance(BigDecimal newBalance) {
        return new Account(id, newBalance);
    }
}
