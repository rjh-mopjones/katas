package com.katas.k01_orderbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 3 — market orders and self-trade prevention. */
@Disabled("locked — run: ./kata 01")
class Stage3Test {

    @Test
    void a_market_order_sweeps_the_best_prices() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.SELL, 100, 3));
        book.submit(Order.limit(2, "a", Side.SELL, 101, 3));

        List<Fill> fills = book.submit(Order.market(3, "b", Side.BUY, 4));

        assertThat(fills).containsExactly(new Fill(1, 3, 100, 3), new Fill(2, 3, 101, 1));
        assertThat(book.bestAsk()).hasValue(101);
    }

    @Test
    void a_market_order_remainder_does_not_rest() {
        OrderBook book = new OrderBook();
        book.submit(Order.limit(1, "a", Side.SELL, 100, 2));

        List<Fill> fills = book.submit(Order.market(2, "b", Side.BUY, 10)); // only 2 available

        assertThat(fills).containsExactly(new Fill(1, 2, 100, 2));
        assertThat(book.bestBid()).isEmpty(); // remainder discarded, does NOT rest
        assertThat(book.bestAsk()).isEmpty();
    }

    @Test
    void a_market_order_into_an_empty_book_produces_no_fills() {
        OrderBook book = new OrderBook();
        assertThat(book.submit(Order.market(1, "b", Side.BUY, 5))).isEmpty();
    }

    @Test
    void stp_cancel_resting_removes_the_maker_and_continues() {
        OrderBook book = new OrderBook(StpPolicy.CANCEL_RESTING);
        book.submit(Order.limit(1, "acc", Side.SELL, 100, 5));   // resting, same account
        book.submit(Order.limit(2, "other", Side.SELL, 100, 5)); // resting, other account

        List<Fill> fills = book.submit(Order.limit(3, "acc", Side.BUY, 100, 8));

        // order 1 (same account) is cancelled; order 2 fills 5; remainder 3 rests
        assertThat(fills).containsExactly(new Fill(2, 3, 100, 5));
        assertThat(book.cancel(1)).isFalse(); // order 1 was removed by STP
        assertThat(book.bestBid()).hasValue(100); // residual 3 rests
    }

    @Test
    void stp_cancel_newest_drops_the_taker_remainder() {
        OrderBook book = new OrderBook(StpPolicy.CANCEL_NEWEST);
        book.submit(Order.limit(1, "acc", Side.SELL, 100, 5));

        List<Fill> fills = book.submit(Order.limit(2, "acc", Side.BUY, 100, 5)); // would self-match

        assertThat(fills).isEmpty();              // newest cancelled, no trade
        assertThat(book.bestAsk()).hasValue(100); // resting order untouched
        assertThat(book.bestBid()).isEmpty();     // taker did not rest
    }
}
