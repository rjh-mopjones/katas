package com.katas.k02_oddsfeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 4 — backpressure via conflation (ConflatingBuffer). */
@Disabled("locked — run: ./kata 02")
class Stage4Test {

    private static OddsUpdate update(long seq, String selection) {
        return new OddsUpdate(seq, "BET365", "E", "M", selection, BigDecimal.valueOf(seq));
    }

    @Test
    void offers_for_one_selection_conflate_to_the_highest_seq() {
        ConflatingBuffer buffer = new ConflatingBuffer();

        buffer.offer(update(1, "HOME"));
        buffer.offer(update(2, "HOME"));
        buffer.offer(update(3, "HOME"));

        assertThat(buffer.pending()).isEqualTo(1);
        Optional<OddsUpdate> polled = buffer.poll();
        assertThat(polled).isPresent();
        assertThat(polled.get().seq()).isEqualTo(3); // latest wins
        assertThat(buffer.pending()).isEqualTo(0);
    }

    @Test
    void a_lower_seq_offer_after_a_higher_one_is_ignored() {
        ConflatingBuffer buffer = new ConflatingBuffer();

        buffer.offer(update(3, "HOME"));
        buffer.offer(update(2, "HOME")); // stale — must not overwrite seq 3

        assertThat(buffer.pending()).isEqualTo(1);
        assertThat(buffer.poll().orElseThrow().seq()).isEqualTo(3);
    }

    @Test
    void polling_an_empty_buffer_returns_empty() {
        ConflatingBuffer buffer = new ConflatingBuffer();
        assertThat(buffer.poll()).isEmpty();
    }

    @Test
    void pending_counts_distinct_selections_not_offers() {
        ConflatingBuffer buffer = new ConflatingBuffer();

        buffer.offer(update(1, "HOME"));
        buffer.offer(update(1, "AWAY"));
        buffer.offer(update(2, "HOME")); // conflates HOME, does not add a new entry

        assertThat(buffer.pending()).isEqualTo(2);
    }

    @Test
    void concurrent_producer_and_consumer_never_lose_a_selections_latest() throws InterruptedException {
        ConflatingBuffer buffer = new ConflatingBuffer();
        String[] selections = {"HOME", "DRAW", "AWAY", "OVER", "UNDER"};
        long n = 20_000; // last seq offered per selection

        // Highest seq ever observed (by the consumer or the final drain) per selection.
        ConcurrentHashMap<String, Long> maxSeen = new ConcurrentHashMap<>();
        for (String s : selections) {
            maxSeen.put(s, 0L);
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch producerDone = new CountDownLatch(1);
        CountDownLatch consumerDone = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        pool.submit(() -> {
            try {
                start.await();
                for (long seq = 1; seq <= n; seq++) {
                    for (String s : selections) {
                        buffer.offer(update(seq, s));
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                producerDone.countDown();
            }
        });

        pool.submit(() -> {
            try {
                start.await();
                while (producerDone.getCount() > 0) {
                    buffer.poll().ifPresent(u -> maxSeen.merge(u.selection(), u.seq(), Math::max));
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                consumerDone.countDown();
            }
        });

        start.countDown();
        assertThat(producerDone.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(consumerDone.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // Drain whatever the consumer didn't reach before the producer finished.
        Optional<OddsUpdate> u;
        while ((u = buffer.poll()).isPresent()) {
            maxSeen.merge(u.get().selection(), u.get().seq(), Math::max);
        }

        assertThat(failure.get()).isNull();
        // No lost latest: the last seq offered for every selection was observed exactly once, somewhere.
        for (String s : selections) {
            assertThat(maxSeen.get(s)).as("latest seq for %s", s).isEqualTo(n);
        }
        // Consistent final state: everything has been drained.
        assertThat(buffer.pending()).isEqualTo(0);
    }
}
