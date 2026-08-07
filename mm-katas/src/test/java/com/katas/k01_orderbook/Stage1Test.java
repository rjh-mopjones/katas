package com.katas.k01_orderbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Stage 1 — add / cancel / best quote. No matching yet; orders in this stage never cross. */
class Stage1Test {

    @Test
    void empty_book_has_no_best_quotes() {
        OrderBook book = new OrderBook();
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.bestAsk()).isEmpty();
    }

    @Test
    void resting_orders_set_the_best_quotes() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.BUY, 100, 10));
        book.submit(Order.limit(2, "a", Side.BUY, 101, 5)); // better bid
        book.submit(Order.limit(3, "a", Side.SELL, 105, 8));
        book.submit(Order.limit(4, "a", Side.SELL, 104, 8)); // better ask

        assertThat(book.bestBid()).hasValue(101);
        assertThat(book.bestAsk()).hasValue(104);
    }

    @Test
    void cancel_removes_the_order_and_the_best_falls_back() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.BUY, 100, 10));
        book.submit(Order.limit(2, "a", Side.BUY, 101, 5));

        assertThat(book.cancel(2)).isTrue();
        assertThat(book.bestBid()).hasValue(100);
    }

    @Test
    void cancelling_an_unknown_id_returns_false() {
        OrderBook book = new OrderBook();
        assertThat(book.cancel(999)).isFalse();
    }

    @Test
    void fifo_within_a_price_level_is_preserved() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.BUY, 100, 10));
        book.submit(Order.limit(2, "a", Side.BUY, 100, 20)); // same price, later

        assertThat(book.bestBid()).hasValue(100);
        assertThat(book.cancel(1)).isTrue();
        assertThat(book.bestBid()).hasValue(100); // order 2 still holds the level
        assertThat(book.cancel(2)).isTrue();
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void a_non_positive_quantity_is_rejected() {
        assertThatThrownBy(() -> Order.limit(1, "a", Side.BUY, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
