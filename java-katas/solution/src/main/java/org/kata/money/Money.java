package org.kata.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An immutable amount of money in a given currency — the textbook exercise in making
 * {@link #equals(Object)}, {@link #hashCode()}, and {@link Comparable#compareTo} mutually
 * consistent for a value type built on {@link BigDecimal}.
 *
 * <p>{@code BigDecimal} carries its scale as part of its state: {@code new BigDecimal("2.0")} and
 * {@code new BigDecimal("2.00")} are <em>not</em> {@link BigDecimal#equals equal} (different scale),
 * even though {@link BigDecimal#compareTo} treats them as the same value. A value type that stores a
 * raw, caller-supplied {@code BigDecimal} and derives {@code equals}/{@code hashCode} from it directly
 * inherits that trap: two amounts a human would call identical end up in different hash buckets and
 * fail {@code equals}. This class closes the gap by normalising the amount to a single canonical scale
 * on construction, so {@code equals}, {@code hashCode}, and {@code compareTo} all agree.
 *
 * <h2>Canonicalisation</h2>
 * Every amount is rescaled to {@value #SCALE} decimal places with {@link RoundingMode#HALF_EVEN}
 * (banker's rounding — the standard for financial arithmetic; it avoids the slight positive bias
 * {@link RoundingMode#HALF_UP} introduces over many roundings) the instant the constructor runs.
 * From then on the field is canonical: {@code Money.of("2.0", "USD")} and
 * {@code Money.of("2.00", "USD")} store the identical {@code BigDecimal} (scale 2, value {@code 2.00}),
 * so field-by-field {@code equals}/{@code hashCode} (via {@link BigDecimal#equals}) and
 * {@link BigDecimal#compareTo} all give the same answer for the same logical amount.
 *
 * <h2>Equality vs ordering across currencies</h2>
 * {@code equals} treats amounts in different currencies as simply unequal (never throws — required
 * for safe use as a {@link java.util.HashMap} / {@link java.util.HashSet} key). {@link #compareTo},
 * however, has no sane cross-currency answer — comparing {@code 5 USD} to {@code 5 GBP} is comparing
 * apples to oranges without a conversion rate — so it <b>throws</b> {@link IllegalArgumentException}
 * rather than silently ordering by currency code or amount alone. That makes {@code compareTo} a
 * <em>partial order</em>: total within one currency, undefined (and loud) across currencies. Callers
 * who need a total order across currencies must convert to a common currency first; this type
 * deliberately does not guess an exchange rate.
 *
 * <h2>Immutability</h2>
 * All fields are {@code final}; there are no mutators. {@link #plus}, {@link #minus}, and
 * {@link #times} return a new {@code Money} rather than modifying the receiver, so a {@code Money}
 * is safe to hand out, cache, or use as a map key without defensive copying.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>{@link java.util.Currency}</b> instead of a raw currency-code {@link String} — validates
 *       the code against ISO 4217 and enables locale-aware formatting.</li>
 *   <li><b>Allocation / splitting</b> — dividing an amount N ways without losing or gaining a cent
 *       (the classic "split a bill 3 ways" rounding-remainder problem).</li>
 *   <li><b>{@code MonetaryAmount}</b> (JSR-354, {@code javax.money}) — the standard library
 *       abstraction this class is a simplified stand-in for.</li>
 * </ul>
 */
public final class Money implements Comparable<Money> {

    /** Canonical decimal scale every amount is normalised to. */
    static final int SCALE = 2;

    private final BigDecimal amount;
    private final String currency;

    public Money(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("currency must not be null");
        }
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_EVEN);
        this.currency = currency;
    }

    /** Parse a decimal string amount, e.g. {@code Money.of("19.99", "USD")}. */
    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    /** Sum with {@code other}, which must be the same currency. */
    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), currency);
    }

    /** Difference from {@code other}, which must be the same currency. */
    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), currency);
    }

    /** Scale this amount by {@code factor} (e.g. a tax rate or a quantity), re-normalised to {@value #SCALE} dp. */
    public Money times(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("factor must not be null");
        }
        return new Money(this.amount.multiply(factor), currency);
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }

    /**
     * Equal iff same currency and same canonical amount. Because the amount is normalised to a fixed
     * scale on construction, this is a plain field comparison — no {@link BigDecimal#compareTo} needed
     * here, unlike every other place in this class that touches an amount.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money other)) {
            return false;
        }
        return amount.equals(other.amount) && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    /**
     * Orders by amount within the same currency.
     *
     * @throws IllegalArgumentException if {@code other} is a different currency — see the class-level
     *     "Equality vs ordering across currencies" note.
     */
    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
