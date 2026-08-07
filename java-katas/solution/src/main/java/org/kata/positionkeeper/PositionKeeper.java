package org.kata.positionkeeper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thread-safe running position and worst-case exposure for a betting exchange, kept per market.
 *
 * <p>Every matched {@link Bet} is folded into a running per-market book by {@link #apply}. Reads —
 * {@link #pnlIfWins} and {@link #worstCaseLiability} — are answered from maintained running totals
 * in O(1) (or O(distinct selections in the market) for the liability scan), never by replaying the
 * bet history. That is the shape a real-time risk engine needs: thousands of bets a second must
 * not degrade the latency of the liability check that gates whether the next bet is even accepted.
 *
 * <h2>Payoff model</h2>
 * Decimal odds {@code O}, stake {@code S}. If a bet's selection is the winning outcome:
 * <ul>
 *   <li>{@code BACK}: profit {@code +S*(O-1)}</li>
 *   <li>{@code LAY}: loss {@code -S*(O-1)}</li>
 * </ul>
 * If the bet's selection does <em>not</em> win:
 * <ul>
 *   <li>{@code BACK}: loss {@code -S}</li>
 *   <li>{@code LAY}: profit {@code +S}</li>
 * </ul>
 * {@link #pnlIfWins(String, String)} is that payoff summed over every bet in the market for a
 * hypothesised winner {@code w}. Rather than replay bets, each market keeps four running numbers —
 * {@code backStakeTotal}, {@code layStakeTotal}, and per-selection {@code backOddsStake[sel] =
 * Σ S*O} / {@code layOddsStake[sel] = Σ S*O} over bets backing/laying {@code sel} — from which:
 * <pre>{@code
 * pnlIfWins(w) = (backOddsStake[w] - backStakeTotal) + (layStakeTotal - layOddsStake[w])
 * }</pre>
 * The first term collapses every back bet at once: backers of {@code w} collect their odds-stake,
 * everyone else's back stake is forfeit. The second term is the lay mirror image.
 *
 * <h2>Worst-case liability</h2>
 * {@link #worstCaseLiability(String)} is the largest possible loss across every outcome the market
 * could resolve to: every selection that has at least one bet on it, <em>plus</em> the "other
 * outcome" case — none of those selections wins, so every back bet loses its stake and every lay
 * bet pays out its stake ({@code pnl = layStakeTotal - backStakeTotal}). The candidate set is
 * bounded by the number of distinct selections actually bet on, not the (possibly unbounded, e.g.
 * "top goalscorer") universe of outcomes — an outcome nobody backed or laid cannot be the worst
 * case, since its pnl is exactly the "other outcome" figure, which is already a candidate.
 * Liability is reported as a non-negative number, {@code max(0, -min pnl)}: a market that cannot
 * lose money reports {@code 0}, not a negative "liability".
 *
 * <h2>Concurrency</h2>
 * Markets are independent books, so contention is scoped per market: a {@link ConcurrentHashMap}
 * maps market id to a {@code Market}, and each {@code Market} is a small mutable aggregate guarded
 * by its own {@link ReentrantLock} — held across both the write in {@code apply} and the reads in
 * {@code pnlIfWins}/{@code worstCaseLiability}, so a reader never observes a torn update (e.g. a
 * stake total updated but its odds-stake bucket not yet). One lock per market is the right grain:
 * a live exchange runs far more markets than any single market has concurrent bets, so
 * lock-per-market already gives near-linear scalability without the bookkeeping cost of finer
 * sharding — the named alternative is sharding the accumulators themselves (e.g. striped atomics
 * per selection), which only pays off once a single market's bet rate saturates its own lock.
 * {@link #totalMatchedStake(String)} is tracked in a separate
 * {@code ConcurrentHashMap<String, BigDecimal>} updated via {@code merge}, independent of any
 * market lock, because it only needs per-key atomicity, not consistency with the market book.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Stake limits</b> — reject {@link #apply} once the resulting
 *       {@link #worstCaseLiability} would exceed a configured cap, turning this from a passive
 *       tracker into an active risk gate.</li>
 *   <li><b>Settlement</b> — once a market is settled, freeze its book and expose realised P&amp;L
 *       instead of a hypothetical {@code pnlIfWins} for every outcome.</li>
 *   <li><b>Sharded accumulators</b> — replace the single per-market lock with striped/atomic
 *       counters if profiling ever shows one market's lock as the bottleneck.</li>
 * </ul>
 */
public final class PositionKeeper {

    private static final class Market {
        private final ReentrantLock lock = new ReentrantLock();
        private final Map<String, BigDecimal> backOddsStake = new HashMap<>();
        private final Map<String, BigDecimal> layOddsStake = new HashMap<>();
        private BigDecimal backStakeTotal = BigDecimal.ZERO;
        private BigDecimal layStakeTotal = BigDecimal.ZERO;

        void apply(Bet bet) {
            BigDecimal stakeOdds = bet.stake().multiply(bet.odds());
            lock.lock();
            try {
                if (bet.side() == Side.BACK) {
                    backStakeTotal = backStakeTotal.add(bet.stake());
                    backOddsStake.merge(bet.selection(), stakeOdds, BigDecimal::add);
                } else {
                    layStakeTotal = layStakeTotal.add(bet.stake());
                    layOddsStake.merge(bet.selection(), stakeOdds, BigDecimal::add);
                }
            } finally {
                lock.unlock();
            }
        }

        BigDecimal pnlIfWins(String selection) {
            lock.lock();
            try {
                return pnlIfWinsLocked(selection);
            } finally {
                lock.unlock();
            }
        }

        /** Caller must hold {@link #lock}. */
        private BigDecimal pnlIfWinsLocked(String selection) {
            BigDecimal backOdds = backOddsStake.getOrDefault(selection, BigDecimal.ZERO);
            BigDecimal layOdds = layOddsStake.getOrDefault(selection, BigDecimal.ZERO);
            return backOdds.subtract(backStakeTotal).add(layStakeTotal).subtract(layOdds);
        }

        BigDecimal worstCaseLiability() {
            lock.lock();
            try {
                BigDecimal otherOutcome = layStakeTotal.subtract(backStakeTotal);
                Set<String> selections = Stream.concat(
                                backOddsStake.keySet().stream(), layOddsStake.keySet().stream())
                        .collect(Collectors.toSet());

                BigDecimal worstPnl = otherOutcome;
                for (String selection : selections) {
                    BigDecimal pnl = pnlIfWinsLocked(selection);
                    if (pnl.compareTo(worstPnl) < 0) {
                        worstPnl = pnl;
                    }
                }
                return worstPnl.negate().max(BigDecimal.ZERO);
            } finally {
                lock.unlock();
            }
        }
    }

    private final ConcurrentHashMap<String, Market> markets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BigDecimal> matchedStakeBySelection = new ConcurrentHashMap<>();

    /** Fold a matched bet into its market's running book. Safe for many concurrent callers. */
    public void apply(Bet bet) {
        Objects.requireNonNull(bet, "bet");
        markets.computeIfAbsent(bet.market(), m -> new Market()).apply(bet);
        matchedStakeBySelection.merge(bet.selection(), bet.stake(), BigDecimal::add);
    }

    /**
     * The whole market's profit/loss if {@code selection} is the winning outcome.
     *
     * @return {@code 0} for an unknown or empty market.
     */
    public BigDecimal pnlIfWins(String market, String selection) {
        Market m = markets.get(market);
        return m == null ? BigDecimal.ZERO : m.pnlIfWins(selection);
    }

    /**
     * The largest possible loss across every outcome the market could resolve to, as a
     * non-negative {@link BigDecimal}.
     *
     * @return {@code 0} for an unknown or empty market, or a market that cannot lose money.
     */
    public BigDecimal worstCaseLiability(String market) {
        Market m = markets.get(market);
        return m == null ? BigDecimal.ZERO : m.worstCaseLiability();
    }

    /** Sum of stakes applied to {@code selection}, across all markets. {@code 0} if unseen. */
    public BigDecimal totalMatchedStake(String selection) {
        return matchedStakeBySelection.getOrDefault(selection, BigDecimal.ZERO);
    }
}
