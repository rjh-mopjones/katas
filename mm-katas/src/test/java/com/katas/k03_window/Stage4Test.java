package com.katas.k03_window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 4 — memory-bounded under high throughput. */
@Disabled("locked — run: ./kata 03")
class Stage4Test {

    @Test
    void memory_is_bounded_regardless_of_event_count() {
        AtomicLong clk = new AtomicLong(0);
        long window = 10_000;
        SlidingWindow w = new SlidingWindow(window, 0, clk::get);

        long now = 0;
        for (int i = 0; i < 1_000_000; i++) {
            now += 1; // advance 1ms per event → one event per millisecond
            clk.set(now);
            w.add(now, 1.0);
        }

        // Retention is O(window) buckets, NOT O(1,000,000) events.
        assertThat(w.retainedBuckets()).isLessThanOrEqualTo((int) window + 1);
        // ...and the rolling count/sum over the last `window` ms are still exact.
        assertThat(w.count()).isEqualTo(window);
        assertThat(w.sum()).isCloseTo((double) window, within(1e-6));
    }

    @Test
    void bucketed_aggregation_matches_a_naive_oracle() {
        AtomicLong clk = new AtomicLong(0);
        long window = 100;
        SlidingWindow w = new SlidingWindow(window, 0, clk::get);

        List<long[]> accepted = new ArrayList<>(); // {ts, value}
        Random rnd = new Random(7);
        long now = 0;
        for (int i = 0; i < 20_000; i++) {
            now += rnd.nextInt(3); // advance 0..2 ms
            clk.set(now);
            long v = rnd.nextInt(100);
            if (w.add(now, v)) {
                accepted.add(new long[] {now, v});
            }
        }

        final long fnow = now;
        long oracleCount = accepted.stream().filter(a -> a[0] > fnow - window && a[0] <= fnow).count();
        double oracleSum = accepted.stream()
                .filter(a -> a[0] > fnow - window && a[0] <= fnow)
                .mapToDouble(a -> a[1])
                .sum();

        assertThat(w.count()).isEqualTo(oracleCount);
        assertThat(w.sum()).isCloseTo(oracleSum, within(1e-6));
    }
}
