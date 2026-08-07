package com.katas.k03_window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 2 — weighted average. */
@Disabled("locked — run: ./kata 03")
class Stage2Test {

    @Test
    void weighted_average_over_the_window() {
        AtomicLong clk = new AtomicLong(0);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        w.add(0, 10, 1);
        clk.set(100);
        w.add(100, 20, 3);
        // (10*1 + 20*3) / (1 + 3) = 70 / 4 = 17.5
        assertThat(w.weightedAverage()).hasValue(17.5);
    }

    @Test
    void empty_window_has_no_weighted_average() {
        AtomicLong clk = new AtomicLong(0);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        assertThat(w.weightedAverage()).isEmpty();
    }

    @Test
    void zero_total_weight_has_no_weighted_average() {
        AtomicLong clk = new AtomicLong(0);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        w.add(0, 10, 0.0);
        assertThat(w.weightedAverage()).isEmpty(); // divide-by-zero guarded
        assertThat(w.count()).isEqualTo(1); // it is still an event
        assertThat(w.sum()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void weights_expire_consistently_with_values() {
        AtomicLong clk = new AtomicLong(0);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        w.add(0, 10, 1);
        clk.set(500);
        w.add(500, 20, 1);
        clk.set(1000); // ts=0 drops — only ts=500 remains
        assertThat(w.weightedAverage()).hasValue(20.0);
    }
}
