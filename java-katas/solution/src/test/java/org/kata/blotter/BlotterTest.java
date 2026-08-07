package org.kata.blotter;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlotterTest {

    private final Blotter blotter = new Blotter();

    private static Trade trade(String desk, String symbol, Side side, String pnl, String... tags) {
        return new Trade(desk, symbol, side, new BigDecimal(pnl), List.of(tags));
    }

    @Test
    void pnl_is_summed_per_desk_and_symbol() {
        List<Trade> trades = List.of(
                trade("rates", "UST10Y", Side.BUY, "100.00"),
                trade("rates", "UST10Y", Side.SELL, "-40.00"),
                trade("rates", "UST2Y", Side.BUY, "10.00"),
                trade("equities", "AAPL", Side.BUY, "5.00"));

        Map<String, Map<String, BigDecimal>> result = blotter.pnlByDeskAndSymbol(trades);

        assertEquals(0, new BigDecimal("60.00").compareTo(result.get("rates").get("UST10Y")));
        assertEquals(0, new BigDecimal("10.00").compareTo(result.get("rates").get("UST2Y")));
        assertEquals(0, new BigDecimal("5.00").compareTo(result.get("equities").get("AAPL")));
    }

    @Test
    void pnl_by_desk_and_symbol_is_empty_map_for_empty_input() {
        assertTrue(blotter.pnlByDeskAndSymbol(List.of()).isEmpty());
    }

    @Test
    void winners_and_losers_are_partitioned_by_positive_pnl() {
        Trade winner = trade("rates", "UST10Y", Side.BUY, "50.00");
        Trade loser = trade("rates", "UST10Y", Side.SELL, "-20.00");
        Trade flat = trade("rates", "UST10Y", Side.SELL, "0.00");

        Map<Boolean, List<Trade>> result = blotter.winnersVsLosers(List.of(winner, loser, flat));

        assertEquals(List.of(winner), result.get(true));
        assertEquals(List.of(loser, flat), result.get(false));
    }

    @Test
    void winners_vs_losers_is_empty_lists_for_empty_input() {
        Map<Boolean, List<Trade>> result = blotter.winnersVsLosers(List.of());

        assertTrue(result.get(true).isEmpty());
        assertTrue(result.get(false).isEmpty());
    }

    @Test
    void min_and_max_pnl_are_found_in_one_pass() {
        List<Trade> trades = List.of(
                trade("rates", "UST10Y", Side.BUY, "50.00"),
                trade("rates", "UST10Y", Side.SELL, "-20.00"),
                trade("equities", "AAPL", Side.BUY, "5.00"));

        Blotter.MinMax result = blotter.minMaxPnl(trades);

        assertEquals(0, new BigDecimal("-20.00").compareTo(result.min()));
        assertEquals(0, new BigDecimal("50.00").compareTo(result.max()));
    }

    @Test
    void min_max_pnl_is_null_for_empty_input() {
        Blotter.MinMax result = blotter.minMaxPnl(List.of());

        assertNull(result.min());
        assertNull(result.max());
    }

    @Test
    void tag_counts_are_totalled_across_trades() {
        List<Trade> trades = List.of(
                trade("rates", "UST10Y", Side.BUY, "50.00", "algo", "hedge"),
                trade("rates", "UST2Y", Side.SELL, "-10.00", "algo"),
                trade("equities", "AAPL", Side.BUY, "5.00", "manual"));

        Map<String, Long> result = blotter.tagCounts(trades);

        assertEquals(2L, result.get("algo"));
        assertEquals(1L, result.get("hedge"));
        assertEquals(1L, result.get("manual"));
    }

    @Test
    void tag_counts_is_empty_map_for_empty_input() {
        assertTrue(blotter.tagCounts(List.of()).isEmpty());
    }

    @Test
    void tag_counts_ignores_trades_with_no_tags() {
        Trade noTags = trade("rates", "UST10Y", Side.BUY, "50.00");

        Map<String, Long> result = blotter.tagCounts(List.of(noTags));

        assertTrue(result.isEmpty());
    }
}
