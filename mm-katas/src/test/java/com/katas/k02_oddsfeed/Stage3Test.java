package com.katas.k02_oddsfeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 3 — per-bookmaker seq tracking and gap detection. */
@Disabled("locked — run: ./kata 02")
class Stage3Test {

    record Gap(String book, long expected, long received) {}

    static final class Recorder implements FeedListener {
        final List<OddsUpdate> updates = new ArrayList<>();
        final List<ParseError> errors = new ArrayList<>();
        final List<Gap> gaps = new ArrayList<>();

        @Override
        public void onUpdate(OddsUpdate update) {
            updates.add(update);
        }

        @Override
        public void onError(ParseError error) {
            errors.add(error);
        }

        @Override
        public void onGap(String book, long expectedSeq, long receivedSeq) {
            gaps.add(new Gap(book, expectedSeq, receivedSeq));
        }
    }

    private static String line(long seq, String book) {
        return seq + "|" + book + "|E|M|S|2.50\n";
    }

    @Test
    void contiguous_sequences_all_deliver_with_no_gaps() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed(line(1, "BET365"));
        parser.feed(line(2, "BET365"));
        parser.feed(line(3, "BET365"));

        assertThat(rec.updates).extracting(OddsUpdate::seq).containsExactly(1L, 2L, 3L);
        assertThat(rec.gaps).isEmpty();
    }

    @Test
    void a_gap_is_reported_once_then_the_update_is_delivered() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed(line(1, "BET365"));
        parser.feed(line(2, "BET365"));
        parser.feed(line(5, "BET365")); // 3 and 4 missing
        parser.feed(line(6, "BET365"));

        assertThat(rec.gaps).containsExactly(new Gap("BET365", 3, 5));
        assertThat(rec.updates).extracting(OddsUpdate::seq).containsExactly(1L, 2L, 5L, 6L);
    }

    @Test
    void a_duplicate_sequence_is_dropped() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed(line(1, "BET365"));
        parser.feed(line(2, "BET365"));
        parser.feed(line(2, "BET365")); // duplicate

        assertThat(rec.updates).extracting(OddsUpdate::seq).containsExactly(1L, 2L);
        assertThat(rec.gaps).isEmpty();
    }

    @Test
    void a_backwards_restart_is_treated_as_old_and_dropped() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed(line(5, "BET365")); // first seen → expected = 5
        parser.feed(line(6, "BET365"));
        parser.feed(line(3, "BET365")); // feed restart, seq goes backwards → dropped

        assertThat(rec.updates).extracting(OddsUpdate::seq).containsExactly(5L, 6L);
    }

    @Test
    void the_first_message_for_a_book_always_delivers_whatever_its_seq() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed(line(1000, "BET365")); // first seq for this book is large

        assertThat(rec.updates).extracting(OddsUpdate::seq).containsExactly(1000L);
        assertThat(rec.gaps).isEmpty(); // no gap on the very first message
    }

    @Test
    void books_are_tracked_independently() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed(line(1, "BET365"));
        parser.feed(line(100, "LADBROKES")); // independent first-seen for LADBROKES
        parser.feed(line(2, "BET365"));
        parser.feed(line(101, "LADBROKES"));

        assertThat(rec.gaps).isEmpty();
        assertThat(rec.updates).extracting(OddsUpdate::book, OddsUpdate::seq)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("BET365", 1L),
                        org.assertj.core.groups.Tuple.tuple("LADBROKES", 100L),
                        org.assertj.core.groups.Tuple.tuple("BET365", 2L),
                        org.assertj.core.groups.Tuple.tuple("LADBROKES", 101L));
    }
}
