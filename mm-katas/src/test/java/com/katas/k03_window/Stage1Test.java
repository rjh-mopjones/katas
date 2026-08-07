package com.katas.k03_window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Stage 1 — rolling count and sum over the last N milliseconds. */
class Stage1Test {

    @Test
    void empty_window_is_zero() {
        AtomicLong clk = new AtomicLong(0);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        assertThat(w.count()).isZero();
        assertThat(w.sum()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void counts_and_sums_values_within_the_window() {
        AtomicLong clk = new AtomicLong(0);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        w.add(0, 10);
        clk.set(500);
        w.add(500, 20);
        clk.set(900);
        w.add(900, 5);
        assertThat(w.count()).isEqualTo(3);
        assertThat(w.sum()).isCloseTo(35.0, within(1e-9));
    }

    @Test
    void expired_events_drop_as_the_clock_advances() {
        AtomicLong clk = new AtomicLong(0);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        w.add(0, 10);
        clk.set(500);
        w.add(500, 20);
        clk.set(1000); // window (0, 1000] — ts=0 now excluded
        assertThat(w.count()).isEqualTo(1);
        assertThat(w.sum()).isCloseTo(20.0, within(1e-9));
        clk.set(1501); // window (501, 1501] — ts=500 now excluded
        assertThat(w.count()).isZero();
    }

    @Test
    void the_trailing_edge_is_exclusive() {
        AtomicLong clk = new AtomicLong(1000);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        w.add(1, 7); // window (0, 1000] — 1 is in
        assertThat(w.count()).isEqualTo(1);
        clk.set(1001); // window (1, 1001] — ts=1 is not > 1, so excluded
        assertThat(w.count()).isZero();
    }

    @Test
    void a_future_event_is_rejected() {
        AtomicLong clk = new AtomicLong(1000);
        SlidingWindow w = new SlidingWindow(1000, clk::get);
        assertThat(w.add(2000, 5)).isFalse();
        assertThat(w.count()).isZero();
    }
}
