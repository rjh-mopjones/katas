package org.kata.orderbook;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;


class OrderBookTest {

    private final OrderBook book = new OrderBook();

    private Order order(Side side, String price, int qty) {
        return new Order(UUID.randomUUID(), side, new BigDecimal(price), qty, Instant.now());
    }

    @Test
    void resting_order_creates_no_trades() {
        var trades = book.submit(order(Side.BUY, "100", 10));
        assertTrue(trades.isEmpty());
        assertEquals(new BigDecimal(100), book.bestBid().orElseThrow());
    }

    @Test
    void crossing_order_creates_trade_at_resting_price() {
        book.submit(order(Side.SELL, "100", 10));
        var trades = book.submit(order(Side.BUY, "105", 10));
        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("100"), trades.get(0).price());
        assertEquals(10, trades.get(0).qty());
        assertTrue(book.bestAsk().isEmpty());
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void partial_fill_leaves_residual() {
        book.submit(order(Side.SELL, "100", 10));
        var trades = book.submit(order(Side.BUY, "100", 4));
        assertEquals(1, trades.size());
        assertEquals(4, trades.get(0).qty());
        assertEquals(new BigDecimal("100"), book.bestAsk().orElseThrow());
    }

    @Test
    void price_time_priority_serves_earlier_order_first() {
        var first = order(Side.SELL, "100", 5);
        var second = order(Side.SELL, "100", 5);
        book.submit(first);
        book.submit(second);
        var trades = book.submit(order(Side.BUY, "100", 5));
        assertEquals(1, trades.size());
        assertEquals(first.id(), trades.get(0).sellOrderId());   // earliest filled first
    }

    @Test
    void best_prices_reflect_book_state() {
        book.submit(order(Side.BUY, "99", 5));
        book.submit(order(Side.BUY, "100", 5));
        book.submit(order(Side.SELL, "101", 5));
        book.submit(order(Side.SELL, "102", 5));
        assertEquals(new BigDecimal("100"), book.bestBid().orElseThrow());   // highest buy
        assertEquals(new BigDecimal("101"), book.bestAsk().orElseThrow());   // lowest sell
    }

    @Test
    void cancel_removes_order() {
        var o = order(Side.BUY, "100", 5);
        book.submit(o);
        assertTrue(book.cancel(o.id()));

    }

    @Test
    void price_time_priority_holds_under_concurrent_rests() throws Exception {
        int N = 50;
        var ids = new java.util.concurrent.ConcurrentLinkedQueue<UUID>();
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()){
            IntStream.range(0, N).forEach(i -> exec.submit(()->{
                gate.await();
                var o =

            }));

        }


    }

    @Test
    void concurrent_submits_match_consistently() throws Exception {

    }
}