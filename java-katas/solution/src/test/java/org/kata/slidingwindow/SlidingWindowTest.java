package org.kata.slidingwindow;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowTest {

    // now starts at 2_000 with a 1_000ms window, so the live window is (1_000, 2_000].
    private final AtomicLong clock = new AtomicLong(2_000);
    private final SlidingWindow window = new SlidingWindow(1_000, clock::get);

    @Test
    void empty_window_reports_zero_and_no_average() {
        assertEquals(0, window.count());
        assertEquals(0.0, window.sum(), 1e-9);
        assertEquals(OptionalDouble.empty(), window.weightedAverage());
        assertEquals(0, window.retainedBuckets());
    }

    @Test
    void count_and_sum_reflect_values_in_window() {
        window.add(1_500, 10.0);
        window.add(2_000, 5.0);
        assertEquals(2, window.count());
        assertEquals(15.0, window.sum(), 1e-9);
    }

    @Test
    void values_drop_out_as_the_clock_advances() {
        window.add(1_200, 10.0);
        window.add(1_800, 5.0);
        clock.set(2_500); // window is now (1_500, 2_500]; the ts=1_200 value has expired
        assertEquals(1, window.count());
        assertEquals(5.0, window.sum(), 1e-9);
    }

    @Test
    void trailing_edge_is_exclusive() {
        window.add(1_500, 7.0);
        clock.set(2_499); // edge = 1_499; ts=1_500 > 1_499 is IN
        assertEquals(1, window.count());
        clock.set(2_500); // edge = now - window = 1_500; ts == edge is OUT
        assertEquals(0, window.count());
    }

    @Test
    void future_timestamp_is_rejected() {
        assertFalse(window.add(2_500, 1.0)); // now = 2_000
        assertEquals(0, window.count());
    }

    @Test
    void weighted_average_is_sum_of_value_weight_over_sum_of_weight() {
        window.add(1_500, 2.0, 3.0);  // contributes 6 to weighted, 3 to weight
        window.add(1_800, 4.0, 1.0);  // contributes 4 to weighted, 1 to weight
        OptionalDouble avg = window.weightedAverage();
        assertTrue(avg.isPresent());
        assertEquals(10.0 / 4.0, avg.getAsDouble(), 1e-9);
    }

    @Test
    void zero_total_weight_yields_empty_average() {
        window.add(1_500, 5.0, 0.0);
        assertEquals(1, window.count());
        assertEquals(OptionalDouble.empty(), window.weightedAverage());
    }

    @Test
    void value_and_weight_sums_expire_together() {
        window.add(1_200, 2.0, 3.0);
        window.add(1_900, 4.0, 1.0);
        clock.set(2_500); // ts=1_200 expires; only (4.0, w=1.0) remains
        OptionalDouble avg = window.weightedAverage();
        assertEquals(4.0, avg.orElseThrow(), 1e-9);
    }

    @Test
    void out_of_order_but_in_window_event_is_counted() {
        window.add(1_800, 1.0);
        window.add(1_200, 2.0); // arrives later but is older; still inside (1_000, 2_000]
        assertEquals(2, window.count());
        assertEquals(3.0, window.sum(), 1e-9);
    }

    @Test
    void event_already_past_the_trailing_edge_is_rejected() {
        clock.set(3_000); // window (2_000, 3_000]
        assertFalse(window.add(1_500, 9.0));
        assertEquals(0, window.count());
    }

    @Test
    void duplicate_timestamps_aggregate_into_one_bucket() {
        window.add(1_500, 1.0);
        window.add(1_500, 2.0);
        window.add(1_500, 3.0);
        assertEquals(3, window.count());
        assertEquals(6.0, window.sum(), 1e-9);
        assertEquals(1, window.retainedBuckets());
    }

    @Test
    void retention_is_bounded_by_the_window_not_the_event_count() {
        // 1_000_000 events spread across only 1_000 distinct milliseconds in (1_000, 2_000]
        for (int i = 0; i < 1_000_000; i++) {
            long ts = 1_001 + (i % 1_000); // timestamps in [1_001, 2_000]
            window.add(ts, 1.0);
        }
        assertTrue(window.retainedBuckets() <= 1_001,
                "retained " + window.retainedBuckets() + " buckets; expected <= window+1");
        assertEquals(1_000_000, window.count());
        assertEquals(1_000_000.0, window.sum(), 1e-3);
    }

    @Test
    void interleaved_arrivals_stay_correct_as_the_clock_moves() {
        window.add(1_500, 1.0);
        window.add(1_600, 1.0); // out of order relative to nothing yet, both in window
        clock.set(2_550);       // window (1_550, 2_550]; ts=1_500 expired, ts=1_600 lives
        assertEquals(1, window.count());
        window.add(2_500, 1.0);
        assertEquals(2, window.count());
        assertEquals(2.0, window.sum(), 1e-9);
    }

    @Test
    void non_positive_window_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(0, clock::get));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindow(-5, clock::get));
    }
}
