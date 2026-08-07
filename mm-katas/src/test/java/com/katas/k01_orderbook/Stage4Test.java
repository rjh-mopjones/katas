package com.katas.k01_orderbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 4 — thread-safe concurrent submission. */
@Disabled("locked — run: ./kata 01")
class Stage4Test {

    @Test
    void concurrent_buys_against_one_ask_conserve_quantity() throws InterruptedException {
        OrderBook book = new OrderBook();
        long supply = 10_000;
        book.submit(Order.limit(0, "seller", Side.SELL, 100, supply)); // resting ask

        int threads = 8;
        int perThread = 2_000; // demand 16,000 > supply 10,000
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong traded = new AtomicLong();
        AtomicLong ids = new AtomicLong(1);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        List<Fill> fills = book.submit(Order.limit(ids.getAndIncrement(), "buyer", Side.BUY, 100, 1));
                        fills.forEach(f -> traded.addAndGet(f.qty()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Exactly `supply` traded — no over-fill (double-selling the ask) and no lost fills.
        assertThat(traded.get()).isEqualTo(supply);
        assertThat(book.bestAsk()).isEmpty();       // the ask was fully consumed
        assertThat(book.bestBid()).hasValue(100);   // the excess demand rests as bids
    }

    @Test
    void concurrent_submit_then_cancel_leaves_a_consistent_book() throws InterruptedException {
        OrderBook book = new OrderBook();
        int threads = 8;
        int perThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong ids = new AtomicLong(1);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        long id = ids.getAndIncrement();
                        // non-crossing bids far below any ask, each cancelled by the same thread
                        book.submit(Order.limit(id, "t", Side.BUY, 50, 1));
                        assertThat(book.cancel(id)).isTrue();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Every order was cancelled by its submitter — the book must end empty and uncorrupted.
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.bestAsk()).isEmpty();
    }
}
