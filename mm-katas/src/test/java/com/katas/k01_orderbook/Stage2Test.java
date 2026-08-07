package com.katas.k01_orderbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 2 — matching with partial fills. */
@Disabled("locked — run: ./kata 01")
class Stage2Test {

    @Test
    void a_crossing_order_matches_and_fills() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.SELL, 100, 10)); // resting ask
        List<Fill> fills = book.submit(Order.limit(2, "b", Side.BUY, 100, 4)); // crosses

        assertThat(fills).containsExactly(new Fill(1, 2, 100, 4));
        assertThat(book.bestAsk()).hasValue(100); // 6 of the ask remains
        assertThat(book.bestBid()).isEmpty();     // buyer fully filled, nothing rests
    }

    @Test
    void a_partial_fill_leaves_the_residual_resting() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.SELL, 100, 4));
        List<Fill> fills = book.submit(Order.limit(2, "b", Side.BUY, 100, 10)); // buys 4, rests 6

        assertThat(fills).containsExactly(new Fill(1, 2, 100, 4));
        assertThat(book.bestBid()).hasValue(100); // residual rests as a bid
        assertThat(book.bestAsk()).isEmpty();     // ask fully consumed, empty level removed
    }

    @Test
    void a_marketable_order_sweeps_multiple_levels_in_price_time_order() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.SELL, 100, 3));
        book.submit(Order.limit(2, "a", Side.SELL, 101, 3));
        book.submit(Order.limit(3, "a", Side.SELL, 100, 2)); // same price as 1, later → after 1

        List<Fill> fills = book.submit(Order.limit(4, "b", Side.BUY, 101, 7));

        assertThat(fills).containsExactly(
                new Fill(1, 4, 100, 3), // best price, earliest
                new Fill(3, 4, 100, 2), // same price, next in FIFO
                new Fill(2, 4, 101, 2)); // next price level
        assertThat(book.bestAsk()).hasValue(101); // order 2 has 1 left
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void quantity_is_conserved_with_no_over_fill() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.SELL, 100, 5));
        List<Fill> fills = book.submit(Order.limit(2, "b", Side.BUY, 100, 5)); // exact

        long traded = fills.stream().mapToLong(Fill::qty).sum();
        assertThat(traded).isEqualTo(5);
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.bestAsk()).isEmpty(); // both fully filled → book empty
    }

    @Test
    void a_non_marketable_limit_simply_rests() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.SELL, 105, 10));
        List<Fill> fills = book.submit(Order.limit(2, "b", Side.BUY, 100, 10)); // below the ask

        assertThat(fills).isEmpty();
        assertThat(book.bestBid()).hasValue(100);
        assertThat(book.bestAsk()).hasValue(105);
    }
}
