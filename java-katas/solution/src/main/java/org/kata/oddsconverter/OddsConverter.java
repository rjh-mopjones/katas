package org.kata.oddsconverter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Converts betting odds between the three quoting conventions bookmakers use — decimal, fractional,
 * and American (moneyline) — and to/from implied probability.
 *
 * <p>All arithmetic uses {@link BigDecimal}, never {@code double}. Odds and probabilities are
 * fractions with no exact binary representation ({@code 1.0 / 3.0} in floating point is not
 * {@code 0.333...} exactly, and repeated conversions compound the drift); a bookmaker rounding a
 * price the wrong way by even a fraction of a cent, at volume, is a real P&amp;L leak. {@link BigDecimal}
 * makes the rounding policy explicit and testable instead of an accident of IEEE 754. Every
 * {@code BigDecimal} comparison in this class (and its tests) uses {@link BigDecimal#compareTo}, never
 * {@link BigDecimal#equals}, because {@code equals} is scale-sensitive: {@code 2.50} and {@code 2.5}
 * compare equal but are not {@code .equals()}.
 *
 * <h2>Rounding policy</h2>
 * Decimal results are rounded {@link RoundingMode#HALF_UP} to a fixed {@value #SCALE} decimal
 * places — enough precision to round-trip cleanly through fractional and American without drift,
 * matching the display precision real sportsbooks quote (2-3 decimal places, occasionally 4).
 * {@code HALF_UP} is the convention bettors expect ("round half away from zero"); {@code HALF_EVEN}
 * (banker's rounding) would be defensible for accounting but surprises anyone checking a price by hand.
 *
 * <h2>Fractional reduction</h2>
 * {@link #fractionalFromDecimal} recovers {@code numerator}/{@code denominator} from
 * {@code decimalOdds - 1} by reading the {@link BigDecimal} as an exact integer ratio
 * ({@code unscaledValue / 10^scale}) and dividing both terms by their {@link BigInteger#gcd}. Working
 * from the exact unscaled representation — rather than looping to test candidate denominators, or
 * casting through {@code double} — keeps the reduction exact: {@code 3.50 - 1 = 2.50 = 250/100},
 * {@code gcd(250, 100) = 50}, reduces to {@code 5/2}.
 *
 * <h2>The even-money boundary</h2>
 * Decimal {@code 2.0}, fractional {@code 1/1}, American {@code +100}, and probability {@code 0.5} all
 * name the same coin-flip price. {@link #americanFromDecimal} treats decimal {@code >= 2.0} as the
 * positive-moneyline branch ({@code (decimal - 1) * 100}), so {@code 2.0} lands exactly on
 * {@code +100} rather than falling into the negative branch — American odds have no {@code -100} or
 * {@code +0}; the sign flips exactly at even money.
 *
 * <h2>Validation</h2>
 * Every method rejects out-of-domain input with {@link IllegalArgumentException}: decimal odds
 * {@code <= 1} (there is no such thing as odds that don't at least return the stake), a probability
 * outside the open interval {@code (0, 1)}, and American odds of exactly {@code 0} (moneyline has no
 * zero — the scale jumps from {@code -100} to {@code +100}). {@link Fractional}'s own compact
 * constructor rejects non-positive terms, so a decimal built from a valid {@link Fractional} is
 * always in-domain.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Hong Kong / Indonesian / Malaysian odds</b> — other regional quoting conventions used by
 *       Asian handicap books; each is a linear transform of decimal odds, so they slot in as more
 *       {@code xFromDecimal}/{@code decimalFromX} pairs.</li>
 *   <li><b>Vig removal</b> — given a two-way (or n-way) market's implied probabilities summing to
 *       {@code > 1}, normalise back to a fair book by dividing each by the sum (proportional) or
 *       solving for the overround (Shin's method).</li>
 *   <li><b>Configurable scale/rounding</b> — thread a {@link java.math.MathContext} through a
 *       constructor instead of a fixed constant, for a caller that quotes to 2 or 3 decimal places.</li>
 * </ul>
 */
public final class OddsConverter {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Decimal odds from a fractional quote: {@code numerator/denominator + 1} (e.g. 5/2 → 3.5). */
    public BigDecimal decimalFromFractional(Fractional fractional) {
        return BigDecimal.valueOf(fractional.numerator())
                .divide(BigDecimal.valueOf(fractional.denominator()), SCALE, ROUNDING)
                .add(BigDecimal.ONE);
    }

    /**
     * Decimal odds from American (moneyline): positive {@code american/100 + 1} (+150 → 2.5),
     * negative {@code 100/|american| + 1} (-200 → 1.5). {@code american == 0} is invalid — moneyline
     * has no zero.
     */
    public BigDecimal decimalFromAmerican(int american) {
        if (american == 0) {
            throw new IllegalArgumentException("american odds cannot be zero");
        }
        if (american > 0) {
            return BigDecimal.valueOf(american)
                    .divide(HUNDRED, SCALE, ROUNDING)
                    .add(BigDecimal.ONE);
        }
        return HUNDRED
                .divide(BigDecimal.valueOf(-american), SCALE, ROUNDING)
                .add(BigDecimal.ONE);
    }

    /** Implied probability {@code 1/decimalOdds}. {@code decimalOdds} must be {@code > 1}. */
    public BigDecimal impliedProbability(BigDecimal decimalOdds) {
        requireValidDecimalOdds(decimalOdds);
        return BigDecimal.ONE.divide(decimalOdds, SCALE, ROUNDING);
    }

    /** Decimal odds {@code 1/p} from an implied probability. {@code p} must be in {@code (0, 1)}. */
    public BigDecimal decimalFromProbability(BigDecimal probability) {
        if (probability.compareTo(BigDecimal.ZERO) <= 0 || probability.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("probability must be in (0, 1): " + probability);
        }
        return BigDecimal.ONE.divide(probability, SCALE, ROUNDING);
    }

    /**
     * Fractional odds reduced to lowest terms from {@code decimalOdds - 1} (e.g. 3.5 → 5/2, 2.0 →
     * 1/1). See the class doc's "Fractional reduction" section for the algorithm.
     */
    public Fractional fractionalFromDecimal(BigDecimal decimalOdds) {
        requireValidDecimalOdds(decimalOdds);
        return reduce(decimalOdds.subtract(BigDecimal.ONE));
    }

    /**
     * American (moneyline) odds from decimal: {@code decimalOdds >= 2.0} rounds
     * {@code (decimalOdds - 1) * 100} to a positive line (2.5 → +150); {@code decimalOdds < 2.0}
     * rounds {@code -100 / (decimalOdds - 1)} to a negative line (1.5 → -200). Even money
     * ({@code 2.0}) lands on the positive branch, giving exactly {@code +100}.
     */
    public int americanFromDecimal(BigDecimal decimalOdds) {
        requireValidDecimalOdds(decimalOdds);
        BigDecimal diff = decimalOdds.subtract(BigDecimal.ONE);
        if (decimalOdds.compareTo(TWO) >= 0) {
            return diff.multiply(HUNDRED).setScale(0, ROUNDING).intValueExact();
        }
        BigDecimal ratio = HUNDRED.divide(diff, SCALE, ROUNDING);
        return -ratio.setScale(0, ROUNDING).intValueExact();
    }

    private static void requireValidDecimalOdds(BigDecimal decimalOdds) {
        if (decimalOdds.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("decimal odds must be greater than 1: " + decimalOdds);
        }
    }

    /** Reduces {@code value} (read as an exact unscaled-integer ratio) to a lowest-terms {@link Fractional}. */
    private static Fractional reduce(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        int scale = normalized.scale();
        BigInteger numerator;
        BigInteger denominator;
        if (scale <= 0) {
            numerator = normalized.unscaledValue().multiply(BigInteger.TEN.pow(-scale));
            denominator = BigInteger.ONE;
        } else {
            numerator = normalized.unscaledValue();
            denominator = BigInteger.TEN.pow(scale);
        }
        BigInteger gcd = numerator.gcd(denominator);
        return new Fractional(numerator.divide(gcd).intValueExact(), denominator.divide(gcd).intValueExact());
    }
}
