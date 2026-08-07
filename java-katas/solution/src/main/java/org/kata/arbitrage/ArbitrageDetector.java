package org.kata.arbitrage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds risk-free ("surebet") arbitrage across bookmakers quoting the same market and computes
 * the stake split that locks in a guaranteed profit regardless of outcome.
 *
 * <p>Shop the best price for every selection in a market across all books, then check whether the
 * sum of the implied probabilities of those best prices is below 100%. If it is, betting a
 * carefully chosen fraction of a bankroll on every selection at its best price guarantees the same
 * payout no matter which selection wins — and that payout exceeds the total staked.
 *
 * <h2>The arbitrage condition</h2>
 * For decimal odds, {@code 1/odds} is the implied probability of that price. Given the best odds
 * {@code o1..on} available for each of the {@code n} selections in a market, define
 * {@code bookSum = Σ(1/oi)}. A single honest bookmaker's own book always sums to <em>more</em> than
 * 1 (their margin, aka the "vig" or "overround"). But shopping the <em>best</em> price per selection
 * across several competing books can push the combined sum <em>below</em> 1 — at that point the
 * market, taken as a whole, is offering odds that no single rational book would offer, and there is
 * a stake split with a strictly positive, riskless payout. {@code bookSum == 1} exactly is
 * break-even, not arbitrage — the strict inequality matters, so every comparison here uses
 * {@link BigDecimal#compareTo}, never {@code ==} or {@code double} equality.
 *
 * <h2>Coverage and tie-breaking</h2>
 * A market is only tradeable if every selection has at least one quote — a missing outcome cannot
 * be hedged, so {@link #isArbitrage} treats it as "no arbitrage" rather than computing a partial
 * book. Where two books quote the exact same best price for a selection, {@link #bestOdds} breaks
 * the tie by book name ascending, so the winner is a deterministic function of the input quotes,
 * independent of list order — useful for reproducible tests and reproducible execution routing.
 *
 * <h2>Stake sizing and rounding</h2>
 * To guarantee an equal payout {@code P} whichever selection wins, stake {@code stakeI = P/oI} on
 * selection {@code i}; summing that over all selections and solving for {@code P} against a fixed
 * bankroll gives {@code stakeI = totalStake * (1/oI) / bookSum}. {@link #stakes} rounds each stake
 * to 2 decimal places ({@link RoundingMode#HALF_UP}, i.e. whole currency cents) because real
 * execution venues do not accept arbitrary-precision stakes. That per-selection rounding is a real
 * subtlety: the rounded stakes can sum to a few cents more or less than {@code totalStake}, and
 * because payout-if-selection-i-wins is {@code stakeI * oI}, rounding also very slightly perturbs
 * the equal-payout property and can erode (rarely, invert) the theoretical edge. A production
 * system typically rounds all but the last stake and assigns the remainder to the last selection
 * (or rounds down and keeps the residual as a safety margin) to preserve the edge exactly;
 * {@link #guaranteedProfit} intentionally reports the <em>theoretical</em> unrounded edge from
 * {@code bookSum} rather than re-deriving it from the rounded stakes, so it is not itself subject to
 * that erosion — callers who need the realized, rounding-adjusted profit should sum the actual
 * rounded payouts themselves.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Streaming quotes</b> — books update prices continuously; re-evaluate {@code bookSum}
 *       incrementally as quotes arrive/expire instead of rescanning the full list, and guard the
 *       shared best-price table with a lock (or shard per market) for concurrent updates.</li>
 *   <li><b>Transaction costs</b> — commission or a maximum stake per book erodes the edge further;
 *       subtract an effective-odds haircut before summing.</li>
 *   <li><b>Stale-price risk</b> — a quote can move or be pulled between detection and execution
 *       ("bet slippage"); a real system would re-validate prices immediately before placing each leg
 *       and abort the whole split if any leg fails.</li>
 *   <li><b>Multi-currency books</b> — convert to a common currency (with its own rounding/erosion
 *       trade-off) before summing implied probabilities.</li>
 * </ul>
 */
public final class ArbitrageDetector {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final int STAKE_SCALE = 2;

    /**
     * The best (highest) quote for every selection that appears at least once in {@code quotes}.
     * Ties on odds are broken by book name ascending, independent of input order.
     */
    public Map<String, Quote> bestOdds(List<Quote> quotes) {
        Map<String, Quote> best = new LinkedHashMap<>();
        for (Quote quote : quotes) {
            Quote current = best.get(quote.selection());
            if (current == null || isBetter(quote, current)) {
                best.put(quote.selection(), quote);
            }
        }
        return best;
    }

    private boolean isBetter(Quote candidate, Quote current) {
        int cmp = candidate.odds().compareTo(current.odds());
        if (cmp != 0) {
            return cmp > 0;
        }
        return candidate.book().compareTo(current.book()) < 0;
    }

    /**
     * Sum, over {@code selections}, of the implied probability ({@code 1/odds}) of each selection's
     * best price. If any selection has no quote at all the market cannot be traded; that case
     * returns {@link BigDecimal#ONE} exactly (a value that reads as "no arbitrage" to
     * {@link #isArbitrage}, since it never satisfies the strict {@code < 1} condition) rather than
     * throwing, so callers can inspect the return value without a try/catch.
     */
    public BigDecimal bookSum(List<Quote> quotes, Set<String> selections) {
        Map<String, Quote> best = bestOdds(quotes);
        if (!best.keySet().containsAll(selections)) {
            return BigDecimal.ONE;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (String selection : selections) {
            BigDecimal odds = best.get(selection).odds();
            sum = sum.add(BigDecimal.ONE.divide(odds, MC), MC);
        }
        return sum;
    }

    /**
     * True iff every selection in {@code selections} has at least one quote and the resulting
     * {@link #bookSum} is <em>strictly</em> less than 1. Break-even ({@code bookSum == 1}) is not an
     * arbitrage — there is no guaranteed profit, only a guaranteed wash.
     */
    public boolean isArbitrage(List<Quote> quotes, Set<String> selections) {
        if (selections.isEmpty()) {
            return false;
        }
        Map<String, Quote> best = bestOdds(quotes);
        if (!best.keySet().containsAll(selections)) {
            return false;
        }
        return bookSum(quotes, selections).compareTo(BigDecimal.ONE) < 0;
    }

    /**
     * The stake per selection that locks in an equal payout whichever selection wins:
     * {@code stakeI = totalStake * (1/oddsI) / bookSum}, rounded to 2 decimal places
     * ({@link RoundingMode#HALF_UP}). See the class Javadoc for the rounding/edge-erosion caveat.
     *
     * @throws IllegalStateException if {@link #isArbitrage} is false for {@code quotes}/{@code
     *     selections} — there is no valid equalising split to return.
     */
    public Map<String, BigDecimal> stakes(List<Quote> quotes, Set<String> selections, BigDecimal totalStake) {
        if (!isArbitrage(quotes, selections)) {
            throw new IllegalStateException("no arbitrage exists for the given quotes/selections");
        }
        Map<String, Quote> best = bestOdds(quotes);
        BigDecimal sum = bookSum(quotes, selections);
        Map<String, BigDecimal> stakes = new LinkedHashMap<>();
        for (String selection : selections) {
            BigDecimal odds = best.get(selection).odds();
            BigDecimal impliedShare = BigDecimal.ONE.divide(odds, MC);
            BigDecimal stake = totalStake.multiply(impliedShare, MC).divide(sum, MC);
            stakes.put(selection, stake.setScale(STAKE_SCALE, RoundingMode.HALF_UP));
        }
        return stakes;
    }

    /**
     * The theoretical, unrounded guaranteed profit for staking {@code totalStake} across the
     * arbitrage: {@code totalStake * (1/bookSum) - totalStake}, rounded to 2 decimal places for
     * currency display. Returns {@link BigDecimal#ZERO} — not a throw — when no arbitrage exists,
     * since "zero guaranteed profit" is itself a meaningful, valid answer (unlike {@link #stakes},
     * which has no valid split to return).
     */
    public BigDecimal guaranteedProfit(List<Quote> quotes, Set<String> selections, BigDecimal totalStake) {
        if (!isArbitrage(quotes, selections)) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = bookSum(quotes, selections);
        BigDecimal payout = totalStake.divide(sum, MC);
        return payout.subtract(totalStake).setScale(STAKE_SCALE, RoundingMode.HALF_UP);
    }
}
