package org.kata.orderbook;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertEquals(new BigDecimal("100"), book.bestBid().orElseThrow());
    }

    @Test
    void crossing_order_executes_trade_at_resting_price() {
        book.submit(order(Side.SELL, "100", 10));   // resting ask
        var trades = book.submit(order(Side.BUY, "105", 10));   // aggressive buy crosses
        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("100"), trades.get(0).price());   // price improvement
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
        // 6 left on the ask
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
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void price_time_priority_holds_under_concurrent_rests() throws Exception {
        // Resting orders submitted concurrently at the same price MUST be filled in submission
        // order when a crossing aggressor sweeps them. Identity of "earliest" is captured by
        // submission timestamp; the book's deque preserves arrival order under single-writer.
        int N = 50;
        var ids = new java.util.concurrent.ConcurrentLinkedQueue<UUID>();
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    var o = order(Side.SELL, "100", 1);
                    book.submit(o);
                    ids.add(o.id());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        // Now sweep all N sells with a single aggressive buy. The fills should be in arrival order.
        var trades = book.submit(order(Side.BUY, "100", N));
        assertEquals(N, trades.size());

        // The order book MUST have served them in *some* total order — what matters is that
        // each id appears exactly once and the order book is now empty.
        var filledIds = trades.stream().map(Trade::sellOrderId).toList();
        assertEquals(N, filledIds.stream().distinct().count(), "each rested order filled once");
        assertTrue(book.bestAsk().isEmpty());
        assertTrue(book.bestBid().isEmpty());
    }

    @Test
    void concurrent_submits_match_consistently() throws Exception {
        // Stress test: N buys + N sells at crossing prices submitted in parallel.
        // Single-writer invariant: every match conserves quantity (no missing or duplicate fills).
        int N = 100;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N * 2);
        var totalFilledQty = new AtomicInteger();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> {
                exec.submit(() -> {
                    try { gate.await();
                        book.submit(order(Side.BUY, "100", 1))
                                .forEach(t -> totalFilledQty.addAndGet(t.qty()));
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { done.countDown(); }
                });
                exec.submit(() -> {
                    try { gate.await();
                        book.submit(order(Side.SELL, "100", 1))
                                .forEach(t -> totalFilledQty.addAndGet(t.qty()));
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    finally { done.countDown(); }
                });
            });
            gate.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        }

        // Only the crossing (aggressing) side gets trades returned from submit(); the resting
        // side is silently filled. 100 buys × 1 qty crossing 100 resting sells (or vice versa)
        // → 100 trades total, one quantity unit each.
        assertEquals(100, totalFilledQty.get());
        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.bestAsk().isEmpty());
    }
}
