package org.kata.arbitrage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArbitrageDetector {

    public Map<String, Quote> bestOdds(List<Quote> quotes) {
        throw new UnsupportedOperationException();
    }

    public BigDecimal bookSum(List<Quote> quotes, Set<String> selections) {
        throw new UnsupportedOperationException();
    }

    public boolean isArbitrage(List<Quote> quotes, Set<String> selections) {
        throw new UnsupportedOperationException();
    }

    public Map<String, BigDecimal> stakes(List<Quote> quotes, Set<String> selections, BigDecimal totalStake) {
        throw new UnsupportedOperationException();
    }

    public BigDecimal guaranteedProfit(List<Quote> quotes, Set<String> selections, BigDecimal totalStake) {
        throw new UnsupportedOperationException();
    }
}
