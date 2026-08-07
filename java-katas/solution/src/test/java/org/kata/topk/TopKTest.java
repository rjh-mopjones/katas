package org.kata.topk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TopKTest {

    @Test
    void top_orders_by_score_descending() {
        TopK topK = new TopK(3);
        topK.add("AAPL", 10);
        topK.add("MSFT", 30);
        topK.add("GOOG", 20);

        assertEquals(
                List.of(new Entry("MSFT", 30), new Entry("GOOG", 20), new Entry("AAPL", 10)),
                topK.top());
    }

    @Test
    void fewer_than_k_distinct_keys_returns_all_of_them() {
        TopK topK = new TopK(5);
        topK.add("AAPL", 10);
        topK.add("MSFT", 20);

        assertEquals(List.of(new Entry("MSFT", 20), new Entry("AAPL", 10)), topK.top());
    }

    @Test
    void tied_scores_break_ties_by_key_ascending() {
        TopK topK = new TopK(3);
        topK.add("MSFT", 10);
        topK.add("AAPL", 10);
        topK.add("GOOG", 10);

        assertEquals(
                List.of(new Entry("AAPL", 10), new Entry("GOOG", 10), new Entry("MSFT", 10)),
                topK.top());
    }

    @Test
    void updating_a_key_can_climb_it_into_the_top_k() {
        TopK topK = new TopK(2);
        topK.add("AAPL", 100);
        topK.add("MSFT", 90);
        topK.add("GOOG", 10); // outside top 2

        topK.add("GOOG", 200); // now 210, climbs to first place

        assertEquals(List.of(new Entry("GOOG", 210), new Entry("AAPL", 100)), topK.top());
    }

    @Test
    void updating_a_key_can_drop_it_out_of_the_top_k() {
        TopK topK = new TopK(2);
        topK.add("AAPL", 100);
        topK.add("MSFT", 90);
        topK.add("GOOG", 10);

        topK.add("AAPL", -95); // drops to 5, now last

        assertEquals(List.of(new Entry("MSFT", 90), new Entry("GOOG", 10)), topK.top());
    }

    @Test
    void increment_adds_one_to_score() {
        TopK topK = new TopK(1);
        topK.increment("AAPL");
        topK.increment("AAPL");
        topK.increment("AAPL");

        assertEquals(3, topK.scoreOf("AAPL"));
        assertEquals(List.of(new Entry("AAPL", 3)), topK.top());
    }

    @Test
    void negative_weight_lowers_score_and_rank() {
        TopK topK = new TopK(2);
        topK.add("AAPL", 50);
        topK.add("MSFT", 40);

        topK.add("AAPL", -20);

        assertEquals(30, topK.scoreOf("AAPL"));
        assertEquals(List.of(new Entry("MSFT", 40), new Entry("AAPL", 30)), topK.top());
    }

    @Test
    void k_of_zero_always_returns_empty() {
        TopK topK = new TopK(0);
        topK.add("AAPL", 100);

        assertTrue(topK.top().isEmpty());
    }

    @Test
    void score_of_unseen_key_is_zero() {
        TopK topK = new TopK(3);
        assertEquals(0, topK.scoreOf("AAPL"));
    }

    @Test
    void empty_stream_yields_empty_top() {
        TopK topK = new TopK(3);
        assertTrue(topK.top().isEmpty());
    }

    @Test
    void k_larger_than_distinct_keys_returns_all_without_padding() {
        TopK topK = new TopK(10);
        topK.add("AAPL", 5);
        topK.add("MSFT", 3);

        List<Entry> top = topK.top();
        assertEquals(2, top.size());
        assertEquals(new Entry("AAPL", 5), top.get(0));
        assertEquals(new Entry("MSFT", 3), top.get(1));
    }

    @Test
    void negative_k_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new TopK(-1));
    }
}
