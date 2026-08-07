package org.kata.overround;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Bookmaker overround ("vig"/"juice") arithmetic: measure a market's built-in margin from its
 * decimal odds and strip it back out to recover the underlying <em>fair</em> probabilities.
 *
 * <p>A decimal-odds market implies a probability per outcome, {@code p = 1/odds}. A perfectly fair
 * book has those implied probabilities sum to exactly {@code 1.0}. A real bookmaker shortens every
 * price slightly so the book sums to <em>more</em> than {@code 1.0} — the excess is the overround,
 * the house edge baked into the prices. {@link #bookSum}, {@link #overround} measure it;
 * {@link #fairProbabilities} and {@link #fairOdds} remove it ("de-vig") via one of three standard
 * methods (see {@link Method}).
 *
 * <h2>Arithmetic</h2>
 * All internal division uses {@link BigDecimal} at a fixed working scale ({@value #SCALE} places,
 * {@link RoundingMode#HALF_UP}) rather than {@code double} — odds and stake maths in a real trading
 * system never uses binary floating point, since {@code 1/3} and similar fractions have no exact
 * binary representation and errors compound across a multi-leg book. The one exception is the
 * {@link Method#POWER} root-find, which is solved in {@code double} (bisection needs many cheap
 * iterations) and only converted back to {@link BigDecimal} for the final result.
 *
 * <h2>Why probabilities are renormalised</h2>
 * Every method's raw output is divided by its own total before being returned. For
 * {@link Method#PROPORTIONAL} and {@link Method#POWER} this is close to a no-op (the construction
 * already sums to ~1, modulo rounding); for {@link Method#ADDITIVE} it is load-bearing — clamping a
 * longshot's probability at zero (see below) leaves the remaining mass short of 1, and
 * renormalising is what restores the "probabilities sum to 1" contract uniformly across all three
 * methods.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Shin's method</b> — a fourth de-vig model that solves for an implied insider-trading
 *       fraction rather than a flat exponent; strictly more accurate than {@link Method#POWER} for
 *       markets with informed money, at the cost of a 2D solve.</li>
 *   <li><b>American / fractional odds</b> — accept an odds format enum and convert to decimal at the
 *       boundary instead of requiring the caller to pre-convert.</li>
 *   <li><b>Arbitrage detection</b> — expose a helper that flags {@code overround < 0} (a mispriced,
 *       arbable book) distinctly from the normal positive-margin case.</li>
 * </ul>
 */
public final class Overround {

    private static final int SCALE = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final int MAX_BISECTION_ITERATIONS = 200;
    private static final double BISECTION_TOLERANCE = 1e-12;

    /** Σ (1/oddsᵢ) — the book total. A fair book sums to exactly {@code 1.0}. */
    public BigDecimal bookSum(List<BigDecimal> decimalOdds) {
        validate(decimalOdds);
        return sum(impliedProbabilities(decimalOdds));
    }

    /** {@code bookSum - 1} — the margin the bookmaker has loaded onto the market (e.g. {@code 0.05} = 5%). */
    public BigDecimal overround(List<BigDecimal> decimalOdds) {
        return bookSum(decimalOdds).subtract(BigDecimal.ONE);
    }

    /**
     * Fair (de-vigged) probabilities, summing to {@code 1} within rounding error, computed by
     * {@code method}.
     *
     * @throws IllegalArgumentException if {@code decimalOdds} is empty or any price is {@code <= 1}
     */
    public List<BigDecimal> fairProbabilities(List<BigDecimal> decimalOdds, Method method) {
        validate(decimalOdds);
        List<BigDecimal> implied = impliedProbabilities(decimalOdds);
        return switch (method) {
            case PROPORTIONAL -> normalize(implied);
            case ADDITIVE -> normalize(additiveAdjust(implied));
            case POWER -> normalize(powerAdjust(implied));
        };
    }

    /**
     * Fair odds ({@code 1/p}) implied by {@code method}.
     *
     * <p>Note: for {@link Method#ADDITIVE}, an extreme longshot's fair probability can round to
     * exactly zero after clamping, which would make its fair odds infinite; this method will throw
     * {@link ArithmeticException} in that case (division by zero) — a real symptom of the additive
     * method's known weakness, not a bug to hide.
     */
    public List<BigDecimal> fairOdds(List<BigDecimal> decimalOdds, Method method) {
        List<BigDecimal> probabilities = fairProbabilities(decimalOdds, method);
        List<BigDecimal> odds = new ArrayList<>(probabilities.size());
        for (BigDecimal p : probabilities) {
            odds.add(BigDecimal.ONE.divide(p, SCALE, ROUNDING));
        }
        return odds;
    }

    /** {@code pᵢ = 1/oᵢ − (bookSum − 1)/n}, an equal absolute share of the margin removed from every
     * outcome. Negative results (a known weakness for extreme longshots once the equal share
     * exceeds their tiny implied probability) are clamped to zero before the caller renormalises. */
    private static List<BigDecimal> additiveAdjust(List<BigDecimal> implied) {
        BigDecimal margin = sum(implied).subtract(BigDecimal.ONE);
        BigDecimal share = margin.divide(BigDecimal.valueOf(implied.size()), SCALE, ROUNDING);
        List<BigDecimal> adjusted = new ArrayList<>(implied.size());
        for (BigDecimal p : implied) {
            BigDecimal shifted = p.subtract(share);
            adjusted.add(shifted.max(BigDecimal.ZERO));
        }
        return adjusted;
    }

    /** {@code pᵢ = (1/oᵢ)^k} for the exponent {@code k} found by {@link #solveExponent}. */
    private static List<BigDecimal> powerAdjust(List<BigDecimal> implied) {
        double k = solveExponent(implied);
        List<BigDecimal> powered = new ArrayList<>(implied.size());
        for (BigDecimal p : implied) {
            double raised = Math.pow(p.doubleValue(), k);
            powered.add(BigDecimal.valueOf(raised).setScale(SCALE, ROUNDING));
        }
        return powered;
    }

    /**
     * Bisects for the exponent {@code k} such that {@code Σ pᵢ^k = 1}, i.e. the root of
     * {@code f(k) = Σ pᵢ^k − 1}.
     *
     * <p>Each {@code pᵢ ∈ (0, 1)} (guaranteed by {@link #validate}, since odds {@code > 1}), so
     * {@code pᵢ^k} is strictly decreasing in {@code k}: {@code f(0) = n − 1 ≥ 0} and
     * {@code f(k) → −1} as {@code k → ∞}. That monotonicity means a single bracket
     * {@code [0, hi]} — doubling {@code hi} until {@code f(hi) ≤ 0} — always contains exactly one
     * root, so plain bisection (no Newton step, no derivative) converges reliably regardless of
     * whether the book has positive or negative overround. {@code k > 1} when the book has positive
     * overround (the exponent shrinks every probability); {@code k < 1} for an arbable, negative-
     * overround book.
     */
    private static double solveExponent(List<BigDecimal> implied) {
        double[] p = new double[implied.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = implied.get(i).doubleValue();
        }

        double lo = 0.0;
        double hi = 1.0;
        while (residual(p, hi) > 0 && hi < 1e6) {
            hi *= 2;
        }

        for (int i = 0; i < MAX_BISECTION_ITERATIONS; i++) {
            double mid = (lo + hi) / 2;
            double residual = residual(p, mid);
            if (Math.abs(residual) < BISECTION_TOLERANCE) {
                return mid;
            }
            if (residual > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    private static double residual(double[] p, double k) {
        double total = 0.0;
        for (double pi : p) {
            total += Math.pow(pi, k);
        }
        return total - 1.0;
    }

    private static List<BigDecimal> impliedProbabilities(List<BigDecimal> decimalOdds) {
        List<BigDecimal> implied = new ArrayList<>(decimalOdds.size());
        for (BigDecimal odds : decimalOdds) {
            implied.add(BigDecimal.ONE.divide(odds, SCALE, ROUNDING));
        }
        return implied;
    }

    /** Scales every value by the reciprocal of the group's own total, so the result sums to 1. */
    private static List<BigDecimal> normalize(List<BigDecimal> raw) {
        BigDecimal total = sum(raw);
        List<BigDecimal> normalized = new ArrayList<>(raw.size());
        for (BigDecimal value : raw) {
            normalized.add(value.divide(total, SCALE, ROUNDING));
        }
        return normalized;
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }

    private static void validate(List<BigDecimal> decimalOdds) {
        if (decimalOdds == null || decimalOdds.isEmpty()) {
            throw new IllegalArgumentException("decimalOdds must not be empty");
        }
        for (BigDecimal odds : decimalOdds) {
            if (odds.compareTo(BigDecimal.ONE) <= 0) {
                throw new IllegalArgumentException("odds must be > 1: " + odds);
            }
        }
    }
}
