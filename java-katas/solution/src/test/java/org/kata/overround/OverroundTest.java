package org.kata.overround;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverroundTest {

    private static final double DELTA = 1e-9;

    private final Overround overround = new Overround();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static double sum(List<BigDecimal> values) {
        double total = 0.0;
        for (BigDecimal v : values) {
            total += v.doubleValue();
        }
        return total;
    }

    @Test
    void book_sum_and_overround_on_a_known_two_way_book() {
        List<BigDecimal> odds = List.of(bd("1.90"), bd("1.90"));
        assertEquals(1.0526315790, overround.bookSum(odds).doubleValue(), 1e-8);
        assertEquals(0.0526315790, overround.overround(odds).doubleValue(), 1e-8);
    }

    @Test
    void fair_book_has_zero_overround_and_every_method_agrees_on_fifty_fifty() {
        List<BigDecimal> odds = List.of(bd("2.0"), bd("2.0"));
        assertEquals(0.0, overround.overround(odds).doubleValue(), DELTA);

        for (Method method : Method.values()) {
            List<BigDecimal> fair = overround.fairProbabilities(odds, method);
            assertEquals(0.5, fair.get(0).doubleValue(), DELTA, method + " outcome 0");
            assertEquals(0.5, fair.get(1).doubleValue(), DELTA, method + " outcome 1");
        }
    }

    @Test
    void every_de_vig_method_returns_probabilities_summing_to_one_for_a_three_way_market() {
        List<BigDecimal> odds = List.of(bd("1.90"), bd("3.60"), bd("4.20"));
        for (Method method : Method.values()) {
            List<BigDecimal> fair = overround.fairProbabilities(odds, method);
            assertEquals(3, fair.size());
            assertEquals(1.0, sum(fair), DELTA, method + " should sum to 1");
        }
    }

    @Test
    void proportional_method_gives_exact_values_on_a_simple_book() {
        // Three equal-priced runners: each implied probability is 0.5, so proportional
        // normalisation gives exactly 1/3 to each.
        List<BigDecimal> odds = List.of(bd("2.0"), bd("2.0"), bd("2.0"));
        List<BigDecimal> fair = overround.fairProbabilities(odds, Method.PROPORTIONAL);
        for (BigDecimal p : fair) {
            assertEquals(1.0 / 3.0, p.doubleValue(), DELTA);
        }
    }

    @Test
    void additive_method_removes_an_equal_absolute_share_of_the_margin() {
        List<BigDecimal> odds = List.of(bd("2.0"), bd("2.0"), bd("2.0"));
        List<BigDecimal> fair = overround.fairProbabilities(odds, Method.ADDITIVE);
        assertEquals(1.0, sum(fair), DELTA);
        for (BigDecimal p : fair) {
            assertEquals(1.0 / 3.0, p.doubleValue(), DELTA);
        }
    }

    @Test
    void power_method_converges_and_its_probabilities_sum_to_one() {
        List<BigDecimal> odds = List.of(bd("1.50"), bd("4.00"), bd("6.00"));
        List<BigDecimal> fair = overround.fairProbabilities(odds, Method.POWER);
        assertEquals(1.0, sum(fair), DELTA);
        // Favourite (shortest odds) should still have the highest fair probability.
        assertTrue(fair.get(0).compareTo(fair.get(1)) > 0);
        assertTrue(fair.get(1).compareTo(fair.get(2)) > 0);
    }

    @Test
    void fair_odds_are_the_reciprocal_of_fair_probabilities() {
        List<BigDecimal> odds = List.of(bd("1.90"), bd("3.60"), bd("4.20"));
        List<BigDecimal> fairProbabilities = overround.fairProbabilities(odds, Method.PROPORTIONAL);
        List<BigDecimal> fairOdds = overround.fairOdds(odds, Method.PROPORTIONAL);
        for (int i = 0; i < odds.size(); i++) {
            assertEquals(1.0 / fairProbabilities.get(i).doubleValue(), fairOdds.get(i).doubleValue(), 1e-6);
        }
    }

    @Test
    void odds_at_or_below_one_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> overround.bookSum(List.of(bd("1.0"), bd("2.0"))));
        assertThrows(IllegalArgumentException.class, () -> overround.bookSum(List.of(bd("0.5"), bd("2.0"))));
        assertThrows(IllegalArgumentException.class,
                () -> overround.fairProbabilities(List.of(bd("1.0")), Method.PROPORTIONAL));
    }

    @Test
    void empty_market_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> overround.bookSum(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> overround.fairProbabilities(List.of(), Method.POWER));
    }
}
