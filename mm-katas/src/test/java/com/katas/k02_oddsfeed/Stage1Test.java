package com.katas.k02_oddsfeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stage 1 — parse + dispatch. feed() is handed whole, newline-terminated lines. */
class Stage1Test {

    /** Records everything the parser dispatches so a test can assert on it. */
    static final class Recorder implements FeedListener {
        final List<OddsUpdate> updates = new ArrayList<>();
        final List<ParseError> errors = new ArrayList<>();
        final List<long[]> gaps = new ArrayList<>();

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
            gaps.add(new long[] {expectedSeq, receivedSeq});
        }
    }

    @Test
    void a_well_formed_line_is_parsed_and_dispatched() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("1|BET365|ARS_v_CHE|MATCH_ODDS|HOME|2.50\n");

        assertThat(rec.errors).isEmpty();
        assertThat(rec.updates).hasSize(1);
        OddsUpdate u = rec.updates.get(0);
        assertThat(u.seq()).isEqualTo(1);
        assertThat(u.book()).isEqualTo("BET365");
        assertThat(u.event()).isEqualTo("ARS_v_CHE");
        assertThat(u.market()).isEqualTo("MATCH_ODDS");
        assertThat(u.selection()).isEqualTo("HOME");
        assertThat(u.odds()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    void odds_are_parsed_as_big_decimal_not_double() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        // 1.10 is not exactly representable as a double; BigDecimal keeps it exact.
        parser.feed("1|BET365|E|M|S|1.10\n");

        assertThat(rec.updates).hasSize(1);
        assertThat(rec.updates.get(0).odds()).isEqualByComparingTo(new BigDecimal("1.10"));
    }

    @Test
    void a_trailing_delimiter_is_seven_fields_and_reported() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("1|BET365|E|M|S|2.50|\n"); // trailing '|' → 7 fields

        assertThat(rec.updates).isEmpty();
        assertThat(rec.errors).hasSize(1);
        assertThat(rec.errors.get(0).reason()).containsIgnoringCase("field");
    }

    @Test
    void a_bad_seq_and_a_bad_odds_are_reported_not_thrown() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("notanumber|BET365|E|M|S|2.50\n");
        parser.feed("2|LADBROKES|E|M|S|xyz\n");

        assertThat(rec.updates).isEmpty();
        assertThat(rec.errors).hasSize(2);
        assertThat(rec.errors.get(0).reason()).containsIgnoringCase("seq");
        assertThat(rec.errors.get(1).reason()).containsIgnoringCase("odds");
    }

    @Test
    void a_blank_line_is_skipped_not_an_error() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("\n");
        parser.feed("   \n");

        assertThat(rec.updates).isEmpty();
        assertThat(rec.errors).isEmpty();
    }

    @Test
    void surrounding_whitespace_around_fields_is_trimmed() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("  1 | BET365 | E | M | HOME | 2.50 \n");

        assertThat(rec.errors).isEmpty();
        assertThat(rec.updates).hasSize(1);
        OddsUpdate u = rec.updates.get(0);
        assertThat(u.book()).isEqualTo("BET365");
        assertThat(u.selection()).isEqualTo("HOME");
        assertThat(u.odds()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    void line_number_is_a_running_counter_across_feed_calls() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("1|BET365|E|M|S|2.50\n"); // line 1 — good
        parser.feed("garbage line\n");         // line 2 — bad

        assertThat(rec.updates).hasSize(1);
        assertThat(rec.errors).hasSize(1);
        assertThat(rec.errors.get(0).lineNumber()).isEqualTo(2);
    }
}
