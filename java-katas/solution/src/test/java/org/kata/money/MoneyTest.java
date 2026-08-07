package org.kata.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static Money usd(String amount) {
        return Money.of(amount, "USD");
    }

    @Test
    void differently_scaled_equal_amounts_compare_equal() {
        assertEquals(usd("2.0"), usd("2.00"));
        assertEquals(usd("2"), usd("2.00"));
    }

    @Test
    void equals_is_reflexive() {
        Money m = usd("10.00");
        assertEquals(m, m);
    }

    @Test
    void equals_is_symmetric() {
        Money a = usd("10.00");
        Money b = usd("10.0");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    void equals_is_transitive() {
        Money a = usd("10.0");
        Money b = usd("10.00");
        Money c = Money.of("10.000", "USD");
        assertEquals(a, b);
        assertEquals(b, c);
        assertEquals(a, c);
    }

    @Test
    void equals_is_consistent_across_repeated_calls() {
        Money a = usd("10.00");
        Money b = usd("10.00");
        for (int i = 0; i < 5; i++) {
            assertEquals(a, b);
        }
    }

    @Test
    void unequal_amount_or_currency_is_not_equal() {
        assertNotEquals(usd("10.00"), usd("10.01"));
        assertNotEquals(usd("10.00"), Money.of("10.00", "GBP"));
    }

    @Test
    void equals_rejects_null_and_other_types() {
        Money m = usd("10.00");
        assertNotEquals(null, m);
        assertNotEquals("10.00 USD", m);
    }

    @Test
    void equal_instances_have_equal_hash_code() {
        Money a = usd("2.0");
        Money b = usd("2.00");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void works_as_a_hash_map_key() {
        Map<Money, String> prices = new HashMap<>();
        prices.put(usd("19.99"), "widget");

        assertEquals("widget", prices.get(Money.of("19.990", "USD")));
        assertTrue(prices.containsKey(usd("19.99")));
    }

    @Test
    void dedups_in_a_hash_set() {
        Set<Money> amounts = new HashSet<>();
        amounts.add(usd("5.0"));
        amounts.add(usd("5.00"));
        amounts.add(usd("5.000"));
        amounts.add(usd("6.00"));

        assertEquals(2, amounts.size());
        assertTrue(amounts.contains(usd("5.00")));
        assertTrue(amounts.contains(usd("6.0")));
    }

    @Test
    void compare_to_orders_amounts_within_a_currency() {
        Money five = usd("5.00");
        Money ten = usd("10.00");

        assertTrue(five.compareTo(ten) < 0);
        assertTrue(ten.compareTo(five) > 0);
        assertEquals(0, five.compareTo(usd("5.0")));
    }

    @Test
    void compare_to_across_currencies_throws() {
        Money usd = usd("5.00");
        Money gbp = Money.of("5.00", "GBP");

        assertThrows(IllegalArgumentException.class, () -> usd.compareTo(gbp));
    }

    @Test
    void plus_adds_same_currency_amounts() {
        assertEquals(usd("15.00"), usd("10.00").plus(usd("5.00")));
    }

    @Test
    void minus_subtracts_same_currency_amounts() {
        assertEquals(usd("5.00"), usd("10.00").minus(usd("5.00")));
    }

    @Test
    void times_scales_by_a_factor() {
        assertEquals(usd("30.00"), usd("10.00").times(BigDecimal.valueOf(3)));
    }

    @Test
    void plus_across_currencies_throws() {
        Money usd = usd("10.00");
        Money gbp = Money.of("10.00", "GBP");
        assertThrows(IllegalArgumentException.class, () -> usd.plus(gbp));
    }

    @Test
    void minus_across_currencies_throws() {
        Money usd = usd("10.00");
        Money gbp = Money.of("10.00", "GBP");
        assertThrows(IllegalArgumentException.class, () -> usd.minus(gbp));
    }

    @Test
    void operations_do_not_mutate_the_operands() {
        Money original = usd("10.00");
        Money unused = original.plus(usd("5.00"));

        assertEquals(usd("10.00"), original);
    }

    @Test
    void constructor_rejects_null_amount_or_currency() {
        assertThrows(IllegalArgumentException.class, () -> new Money(null, "USD"));
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.TEN, null));
    }

    @Test
    void amount_and_currency_accessors_expose_canonical_scale() {
        Money m = usd("2.0");
        assertEquals(0, BigDecimal.valueOf(2.00).compareTo(m.amount()));
        assertEquals(2, m.amount().scale());
        assertEquals("USD", m.currency());
    }
}
