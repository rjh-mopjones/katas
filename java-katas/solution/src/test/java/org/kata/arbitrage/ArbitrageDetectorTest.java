package org.kata.arbitrage;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArbitrageDetectorTest {

    private final ArbitrageDetector detector = new ArbitrageDetector();

    @Test
    void quote_rejects_non_positive_or_unit_odds() {
        assertThrows(IllegalArgumentException.class,
                () -> new Quote("Pinnacle", "A", new BigDecimal("1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> new Quote("Pinnacle", "A", new BigDecimal("0.90")));
        assertThrows(IllegalArgumentException.class, () -> new Quote("", "A", new BigDecimal("2.00")));
    }

    @Test
    void best_odds_picks_the_highest_price_per_selection_and_breaks_ties_by_book_name() {
        List<Quote> quotes = List.of(
                new Quote("Zeta", "A", new BigDecimal("2.00")),
                new Quote("Alpha", "A", new BigDecimal("2.00")), // ties Zeta; "Alpha" < "Zeta"
                new Quote("Beta", "A", new BigDecimal("1.90")),
                new Quote("Gamma", "B", new BigDecimal("3.50")));

        Map<String, Quote> best = detector.bestOdds(quotes);

        assertEquals("Alpha", best.get("A").book());
        assertEquals(0, new BigDecimal("2.00").compareTo(best.get("A").odds()));
        assertEquals("Gamma", best.get("B").book());
    }

    @Test
    void two_way_arbitrage_is_detected_and_stakes_equalise_payout() {
        List<Quote> quotes = List.of(
                new Quote("Pinnacle", "A", new BigDecimal("2.10")),
                new Quote("Bet365", "B", new BigDecimal("2.10")));
        Set<String> selections = Set.of("A", "B");

        assertTrue(detector.isArbitrage(quotes, selections));
        assertEquals(0.952380952, detector.bookSum(quotes, selections).doubleValue(), 1e-6);

        BigDecimal totalStake = new BigDecimal("1000.00");
        Map<String, BigDecimal> stakes = detector.stakes(quotes, selections, totalStake);

        BigDecimal payoutA = stakes.get("A").multiply(new BigDecimal("2.10"));
        BigDecimal payoutB = stakes.get("B").multiply(new BigDecimal("2.10"));

        assertEquals(0, payoutA.compareTo(payoutB), "payout must be equal whichever selection wins");
        assertTrue(payoutA.compareTo(totalStake) > 0, "payout must exceed the amount staked");

        BigDecimal profit = detector.guaranteedProfit(quotes, selections, totalStake);
        assertEquals(0, profit.compareTo(new BigDecimal("50.00")));
    }

    @Test
    void a_single_bookmakers_own_market_is_not_an_arbitrage() {
        List<Quote> quotes = List.of(
                new Quote("Pinnacle", "A", new BigDecimal("1.90")),
                new Quote("Pinnacle", "B", new BigDecimal("1.90")));
        Set<String> selections = Set.of("A", "B");

        assertFalse(detector.isArbitrage(quotes, selections));
        assertTrue(detector.bookSum(quotes, selections).compareTo(BigDecimal.ONE) > 0);
        assertThrows(IllegalStateException.class,
                () -> detector.stakes(quotes, selections, new BigDecimal("1000")));
        assertEquals(0, detector.guaranteedProfit(quotes, selections, new BigDecimal("1000"))
                .compareTo(BigDecimal.ZERO));
    }

    @Test
    void exact_break_even_is_not_an_arbitrage() {
        List<Quote> quotes = List.of(
                new Quote("Pinnacle", "A", new BigDecimal("2.0")),
                new Quote("Bet365", "B", new BigDecimal("2.0")));
        Set<String> selections = Set.of("A", "B");

        BigDecimal sum = detector.bookSum(quotes, selections);
        assertEquals(0, sum.compareTo(BigDecimal.ONE), "1/2.0 + 1/2.0 must sum to exactly 1");
        assertFalse(detector.isArbitrage(quotes, selections));
    }

    @Test
    void a_selection_missing_from_every_quote_is_not_an_arbitrage() {
        List<Quote> quotes = List.of(new Quote("Pinnacle", "A", new BigDecimal("2.10")));
        Set<String> selections = Set.of("A", "B"); // "B" has no quote at all

        assertFalse(detector.isArbitrage(quotes, selections));
        assertThrows(IllegalStateException.class,
                () -> detector.stakes(quotes, selections, new BigDecimal("1000")));
    }

    @Test
    void three_way_arbitrage_is_detected_with_equalised_payouts() {
        List<Quote> quotes = List.of(
                new Quote("Pinnacle", "HOME", new BigDecimal("3.10")),
                new Quote("Bet365", "DRAW", new BigDecimal("3.10")),
                new Quote("William Hill", "AWAY", new BigDecimal("3.10")));
        Set<String> selections = Set.of("HOME", "DRAW", "AWAY");

        assertTrue(detector.isArbitrage(quotes, selections));

        BigDecimal totalStake = new BigDecimal("1000.00");
        Map<String, BigDecimal> stakes = detector.stakes(quotes, selections, totalStake);
        assertEquals(3, stakes.size());

        BigDecimal odds = new BigDecimal("3.10");
        BigDecimal payoutHome = stakes.get("HOME").multiply(odds);
        BigDecimal payoutDraw = stakes.get("DRAW").multiply(odds);
        BigDecimal payoutAway = stakes.get("AWAY").multiply(odds);

        // per-selection rounding to whole cents can perturb equality by a fraction of a cent
        assertEquals(payoutHome.doubleValue(), payoutDraw.doubleValue(), 0.01);
        assertEquals(payoutHome.doubleValue(), payoutAway.doubleValue(), 0.01);
        assertTrue(payoutHome.doubleValue() > totalStake.doubleValue());
    }

    @Test
    void guaranteed_profit_is_positive_and_consistent_with_the_book_sum() {
        List<Quote> quotes = List.of(
                new Quote("Pinnacle", "HOME", new BigDecimal("3.10")),
                new Quote("Bet365", "DRAW", new BigDecimal("3.10")),
                new Quote("William Hill", "AWAY", new BigDecimal("3.10")));
        Set<String> selections = Set.of("HOME", "DRAW", "AWAY");
        BigDecimal totalStake = new BigDecimal("1000.00");

        BigDecimal sum = detector.bookSum(quotes, selections);
        BigDecimal profit = detector.guaranteedProfit(quotes, selections, totalStake);

        assertTrue(profit.compareTo(BigDecimal.ZERO) > 0);
        double expected = totalStake.doubleValue() * (1.0 / sum.doubleValue() - 1.0);
        assertEquals(expected, profit.doubleValue(), 0.01);
    }
}
