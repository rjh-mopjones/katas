package com.katas.k02_oddsfeed;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Stage 2 — malformed + partial lines. feed() is handed arbitrary chunks of the stream. */
@Disabled("locked — run: ./kata 02")
class Stage2Test {

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
    void a_line_split_across_two_feeds_parses_once_whole() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("1|BET365|E|M|S|2.");
        assertThat(rec.updates).isEmpty(); // incomplete — nothing emitted yet
        parser.feed("50\n");

        assertThat(rec.errors).isEmpty();
        assertThat(rec.updates).hasSize(1);
        assertThat(rec.updates.get(0).odds()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    void a_chunk_with_no_newline_emits_nothing_yet() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("1|BET365|E|M|S|2.50"); // no terminator
        assertThat(rec.updates).isEmpty();
        assertThat(rec.errors).isEmpty();

        parser.feed("\n"); // now the line is complete
        assertThat(rec.updates).hasSize(1);
    }

    @Test
    void a_line_split_exactly_at_a_delimiter_parses_once() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("1|BET365|E|M|S|"); // split right at the last '|'
        parser.feed("2.50\n");

        assertThat(rec.errors).isEmpty();
        assertThat(rec.updates).hasSize(1);
        assertThat(rec.updates.get(0).selection()).isEqualTo("S");
    }

    @Test
    void both_crlf_and_lf_terminate_a_line() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        parser.feed("1|BET365|E|M|S|2.50\r\n2|BET365|E|M|S|3.00\n");

        assertThat(rec.errors).isEmpty();
        assertThat(rec.updates).hasSize(2);
        assertThat(rec.updates.get(0).seq()).isEqualTo(1);
        assertThat(rec.updates.get(1).seq()).isEqualTo(2);
        assertThat(rec.updates.get(0).odds()).isEqualByComparingTo(new BigDecimal("2.50"));
    }

    @Test
    void a_malformed_line_mid_stream_does_not_desync_the_lines_after_it() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        // one whole feed containing good, garbage, good
        parser.feed("1|BET365|E|M|S|2.50\nGARBAGE\n2|BET365|E|M|S|3.00\n");

        assertThat(rec.updates).hasSize(2);
        assertThat(rec.updates.get(0).seq()).isEqualTo(1);
        assertThat(rec.updates.get(1).seq()).isEqualTo(2); // the line after the garbage still parses
        assertThat(rec.errors).hasSize(1);
        assertThat(rec.errors.get(0).lineNumber()).isEqualTo(2); // the garbage was line 2
    }

    @Test
    void many_ragged_chunks_reassemble_into_the_right_lines() {
        Recorder rec = new Recorder();
        OddsFeedParser parser = new OddsFeedParser(rec);

        String[] chunks = {"1|BET", "365|E|M|S|2.50\n2|BET365", "|E|M|S|3.0", "0\n3|BET365|E|M|S|4.00\n"};
        for (String c : chunks) {
            parser.feed(c);
        }

        assertThat(rec.errors).isEmpty();
        assertThat(rec.updates).hasSize(3);
        assertThat(rec.updates).extracting(OddsUpdate::seq).containsExactly(1L, 2L, 3L);
    }
}
