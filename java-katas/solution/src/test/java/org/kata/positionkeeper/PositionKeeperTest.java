package org.kata.positionkeeper;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PositionKeeperTest {

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void two_way_market_back_bet_pnl() {
        var keeper = new PositionKeeper();
        keeper.apply(new Bet("Match1", "Home", Side.BACK, new BigDecimal("100"), new BigDecimal("2.0")));
        keeper.apply(new Bet("Match1", "Away", Side.BACK, new BigDecimal("50"), new BigDecimal("3.0")));

        // Home wins: +100*(2-1) on the Home bet, -50 on the losing Away bet.
        assertMoneyEquals("50", keeper.pnlIfWins("Match1", "Home"));
        // Away wins: -100 on the losing Home bet, +50*(3-1) on the Away bet.
        assertMoneyEquals("0", keeper.pnlIfWins("Match1", "Away"));
    }

    @Test
    void three_way_mixed_book_pnl_per_outcome() {
        var keeper = new PositionKeeper();
        keeper.apply(new Bet("Race", "A", Side.BACK, new BigDecimal("10"), new BigDecimal("4")));
        keeper.apply(new Bet("Race", "B", Side.LAY, new BigDecimal("20"), new BigDecimal("2")));
        keeper.apply(new Bet("Race", "C", Side.BACK, new BigDecimal("5"), new BigDecimal("10")));

        // A wins: +10*(4-1) back A, +20 lay B (B loses), -5 back C (C loses) = 45.
        assertMoneyEquals("45", keeper.pnlIfWins("Race", "A"));
        // B wins: -10 back A, -20*(2-1) lay B (B wins), -5 back C = -35.
        assertMoneyEquals("-35", keeper.pnlIfWins("Race", "B"));
        // C wins: -10 back A, +20 lay B, +5*(10-1) back C = 55.
        assertMoneyEquals("55", keeper.pnlIfWins("Race", "C"));
    }

    @Test
    void pure_back_book_worst_case_is_the_other_outcome() {
        var keeper = new PositionKeeper();
        keeper.apply(new Bet("PureBack", "X", Side.BACK, new BigDecimal("100"), new BigDecimal("1.5")));
        keeper.apply(new Bet("PureBack", "Y", Side.BACK, new BigDecimal("100"), new BigDecimal("1.5")));

        // Either X or Y winning still loses 50 on the other leg; neither winning loses both stakes.
        assertMoneyEquals("-50", keeper.pnlIfWins("PureBack", "X"));
        assertMoneyEquals("-50", keeper.pnlIfWins("PureBack", "Y"));
        assertMoneyEquals("200", keeper.worstCaseLiability("PureBack"));
    }

    @Test
    void pure_lay_book_cannot_lose_money() {
        var keeper = new PositionKeeper();
        keeper.apply(new Bet("PureLay", "X", Side.LAY, new BigDecimal("100"), new BigDecimal("1.5")));
        keeper.apply(new Bet("PureLay", "Y", Side.LAY, new BigDecimal("100"), new BigDecimal("1.5")));

        // Every outcome nets a profit for a book with no backers; liability floors at zero, not negative.
        assertMoneyEquals("0", keeper.worstCaseLiability("PureLay"));
    }

    @Test
    void worst_case_liability_across_a_mixed_book_including_other_outcome() {
        var keeper = new PositionKeeper();
        keeper.apply(new Bet("Race", "A", Side.BACK, new BigDecimal("10"), new BigDecimal("4")));
        keeper.apply(new Bet("Race", "B", Side.LAY, new BigDecimal("20"), new BigDecimal("2")));
        keeper.apply(new Bet("Race", "C", Side.BACK, new BigDecimal("5"), new BigDecimal("10")));

        // Candidates: A=45, B=-35, C=55, "other outcome" = layTotal-backTotal = 20-15 = 5.
        // Worst is B winning (pnl -35), so liability is 35.
        assertMoneyEquals("35", keeper.worstCaseLiability("Race"));
    }

    @Test
    void opposing_back_and_lay_at_same_odds_nets_liability_toward_zero() {
        var keeper = new PositionKeeper();
        keeper.apply(new Bet("Hedge", "X", Side.BACK, new BigDecimal("100"), new BigDecimal("2")));
        keeper.apply(new Bet("Hedge", "X", Side.LAY, new BigDecimal("100"), new BigDecimal("2")));

        assertMoneyEquals("0", keeper.pnlIfWins("Hedge", "X"));
        assertMoneyEquals("0", keeper.worstCaseLiability("Hedge"));
    }

    @Test
    void unknown_market_reports_zero() {
        var keeper = new PositionKeeper();
        assertMoneyEquals("0", keeper.pnlIfWins("NoSuchMarket", "X"));
        assertMoneyEquals("0", keeper.worstCaseLiability("NoSuchMarket"));
    }

    @Test
    void bet_validation_throws_on_invalid_stake_and_odds() {
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "X", Side.BACK, new BigDecimal("0"), new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "X", Side.BACK, new BigDecimal("-5"), new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "X", Side.BACK, null, new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "X", Side.BACK, new BigDecimal("10"), new BigDecimal("1")));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "X", Side.BACK, new BigDecimal("10"), new BigDecimal("0.5")));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "X", Side.BACK, new BigDecimal("10"), null));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("", "X", Side.BACK, new BigDecimal("10"), new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "", Side.BACK, new BigDecimal("10"), new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () ->
                new Bet("M", "X", null, new BigDecimal("10"), new BigDecimal("2")));
    }

    @Test
    void concurrent_applies_to_the_same_selection_conserve_total_stake() throws Exception {
        // 200 virtual threads each apply an identical bet on the same selection; the running
        // total must equal N*stake exactly — no lost updates from a race on the shared map.
        var keeper = new PositionKeeper();
        int n = 200;
        BigDecimal stake = new BigDecimal("10");
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(n);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, n).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    keeper.apply(new Bet("ConcMkt", "ConcSel", Side.BACK, stake, new BigDecimal("2")));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertMoneyEquals("2000", keeper.totalMatchedStake("ConcSel"));
    }
}
