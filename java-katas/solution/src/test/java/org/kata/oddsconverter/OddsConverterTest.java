package org.kata.oddsconverter;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OddsConverterTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.001");

    private final OddsConverter converter = new OddsConverter();

    private static void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    private static void assertWithinTolerance(BigDecimal expected, BigDecimal actual) {
        BigDecimal diff = expected.subtract(actual).abs();
        assertTrue(diff.compareTo(TOLERANCE) <= 0,
                () -> "expected " + expected + " to be within " + TOLERANCE + " of " + actual);
    }

    @Test
    void decimal_from_fractional_matches_textbook_values() {
        assertBigDecimalEquals(new BigDecimal("3.5000"), converter.decimalFromFractional(new Fractional(5, 2)));
        assertBigDecimalEquals(new BigDecimal("2.0000"), converter.decimalFromFractional(new Fractional(1, 1)));
    }

    @Test
    void decimal_from_american_handles_both_signs() {
        assertBigDecimalEquals(new BigDecimal("2.5000"), converter.decimalFromAmerican(150));
        assertBigDecimalEquals(new BigDecimal("1.5000"), converter.decimalFromAmerican(-200));
    }

    @Test
    void even_money_boundary_agrees_across_all_four_representations() {
        assertBigDecimalEquals(new BigDecimal("2.0000"), converter.decimalFromFractional(new Fractional(1, 1)));
        assertBigDecimalEquals(new BigDecimal("2.0000"), converter.decimalFromAmerican(100));
        assertEquals(100, converter.americanFromDecimal(new BigDecimal("2.0")));
        assertEquals(1, converter.fractionalFromDecimal(new BigDecimal("2.0")).numerator());
        assertEquals(1, converter.fractionalFromDecimal(new BigDecimal("2.0")).denominator());
        assertBigDecimalEquals(new BigDecimal("0.5000"), converter.impliedProbability(new BigDecimal("2.0")));
    }

    @Test
    void implied_probability_from_decimal_odds() {
        assertBigDecimalEquals(new BigDecimal("0.4000"), converter.impliedProbability(new BigDecimal("2.5")));
        assertBigDecimalEquals(new BigDecimal("0.2857"), converter.impliedProbability(new BigDecimal("3.5")));
    }

    @Test
    void decimal_from_probability_round_trips_with_implied_probability() {
        BigDecimal decimalOdds = new BigDecimal("4.0");
        BigDecimal probability = converter.impliedProbability(decimalOdds);
        BigDecimal roundTripped = converter.decimalFromProbability(probability);
        assertWithinTolerance(decimalOdds, roundTripped);
    }

    @Test
    void fractional_from_decimal_reduces_to_lowest_terms() {
        // 10/4 is the same price as 5/2; both should reduce to the same fractional quote.
        BigDecimal decimalOdds = converter.decimalFromFractional(new Fractional(10, 4));
        Fractional reduced = converter.fractionalFromDecimal(decimalOdds);
        assertEquals(5, reduced.numerator());
        assertEquals(2, reduced.denominator());
    }

    @Test
    void fractional_from_decimal_matches_textbook_values() {
        Fractional fromEvenMoney = converter.fractionalFromDecimal(new BigDecimal("2.0"));
        assertEquals(1, fromEvenMoney.numerator());
        assertEquals(1, fromEvenMoney.denominator());

        Fractional fromThreeHalf = converter.fractionalFromDecimal(new BigDecimal("3.5"));
        assertEquals(5, fromThreeHalf.numerator());
        assertEquals(2, fromThreeHalf.denominator());
    }

    @Test
    void american_from_decimal_matches_textbook_values() {
        assertEquals(150, converter.americanFromDecimal(new BigDecimal("2.5")));
        assertEquals(-200, converter.americanFromDecimal(new BigDecimal("1.5")));
        assertEquals(100, converter.americanFromDecimal(new BigDecimal("2.0")));
    }

    @Test
    void round_trip_decimal_fractional_decimal_stays_within_tolerance() {
        BigDecimal original = new BigDecimal("3.5");
        Fractional fractional = converter.fractionalFromDecimal(original);
        BigDecimal roundTripped = converter.decimalFromFractional(fractional);
        assertWithinTolerance(original, roundTripped);
    }

    @Test
    void round_trip_decimal_american_decimal_stays_within_tolerance() {
        BigDecimal original = new BigDecimal("2.5");
        int american = converter.americanFromDecimal(original);
        BigDecimal roundTripped = converter.decimalFromAmerican(american);
        assertWithinTolerance(original, roundTripped);

        BigDecimal originalUnderdog = new BigDecimal("1.5");
        int americanUnderdog = converter.americanFromDecimal(originalUnderdog);
        BigDecimal roundTrippedUnderdog = converter.decimalFromAmerican(americanUnderdog);
        assertWithinTolerance(originalUnderdog, roundTrippedUnderdog);
    }

    @Test
    void american_odds_of_zero_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> converter.decimalFromAmerican(0));
    }

    @Test
    void implied_probability_rejects_decimal_odds_at_or_below_one() {
        assertThrows(IllegalArgumentException.class, () -> converter.impliedProbability(BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> converter.impliedProbability(new BigDecimal("0.9")));
    }

    @Test
    void decimal_from_probability_rejects_out_of_range_values() {
        assertThrows(IllegalArgumentException.class, () -> converter.decimalFromProbability(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> converter.decimalFromProbability(BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> converter.decimalFromProbability(new BigDecimal("1.5")));
        assertThrows(IllegalArgumentException.class, () -> converter.decimalFromProbability(new BigDecimal("-0.1")));
    }

    @Test
    void fractional_from_decimal_rejects_decimal_odds_at_or_below_one() {
        assertThrows(IllegalArgumentException.class, () -> converter.fractionalFromDecimal(BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> converter.fractionalFromDecimal(new BigDecimal("0.5")));
    }

    @Test
    void american_from_decimal_rejects_decimal_odds_at_or_below_one() {
        assertThrows(IllegalArgumentException.class, () -> converter.americanFromDecimal(BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> converter.americanFromDecimal(new BigDecimal("0.5")));
    }

    @Test
    void fractional_rejects_non_positive_terms() {
        assertThrows(IllegalArgumentException.class, () -> new Fractional(0, 2));
        assertThrows(IllegalArgumentException.class, () -> new Fractional(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new Fractional(-5, 2));
        assertThrows(IllegalArgumentException.class, () -> new Fractional(5, -2));
    }

    @Test
    void fractional_parses_and_prints_board_format() {
        assertEquals(new Fractional(5, 2), Fractional.parse("5/2"));
        assertEquals("5/2", new Fractional(5, 2).toString());
    }
}
