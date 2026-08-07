package com.katas.k03_window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 3 — out-of-order events with a lateness bound. */
@Disabled("locked — run: ./kata 03")
class Stage3Test {

    @Test
    void out_of_order_but_in_window_events_still_count() {
        AtomicLong clk = new AtomicLong(5000);
        SlidingWindow w = new SlidingWindow(1000, 500, clk::get);
        assertThat(w.add(4900, 1)).isTrue();
        assertThat(w.add(4500, 1)).isTrue(); // earlier ts, arrives later — still in (4000, 5000]
        assertThat(w.add(4700, 1)).isTrue();
        assertThat(w.count()).isEqualTo(3);
        assertThat(w.sum()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void an_event_past_the_trailing_edge_is_rejected() {
        AtomicLong clk = new AtomicLong(5000);
        SlidingWindow w = new SlidingWindow(1000, 500, clk::get);
        assertThat(w.add(4000, 1)).isFalse(); // 4000 == now - window → already expired
        assertThat(w.add(3500, 1)).isFalse();
        assertThat(w.count()).isZero();
    }

    @Test
    void duplicate_timestamps_aggregate() {
        AtomicLong clk = new AtomicLong(5000);
        SlidingWindow w = new SlidingWindow(1000, 500, clk::get);
        w.add(4800, 5);
        w.add(4800, 7); // same timestamp
        assertThat(w.count()).isEqualTo(2);
        assertThat(w.sum()).isCloseTo(12.0, within(1e-9));
    }

    @Test
    void interleaved_out_of_order_stays_correct_as_time_advances() {
        AtomicLong clk = new AtomicLong(1000);
        SlidingWindow w = new SlidingWindow(1000, 500, clk::get);
        w.add(200, 1);
        w.add(900, 1);
        w.add(100, 1);
        w.add(500, 1); // window (0, 1000] — all four in
        assertThat(w.count()).isEqualTo(4);
        clk.set(1300); // window (300, 1300] — ts 100, 200 drop
        assertThat(w.count()).isEqualTo(2);
        assertThat(w.sum()).isCloseTo(2.0, within(1e-9));
    }
}
