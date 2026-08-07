package org.kata.blotter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class Blotter {

    public record MinMax(BigDecimal min, BigDecimal max) {
    }

    public Map<String, Map<String, BigDecimal>> pnlByDeskAndSymbol(List<Trade> trades) {
        throw new UnsupportedOperationException();
    }

    public Map<Boolean, List<Trade>> winnersVsLosers(List<Trade> trades) {
        throw new UnsupportedOperationException();
    }

    public MinMax minMaxPnl(List<Trade> trades) {
        throw new UnsupportedOperationException();
    }

    public Map<String, Long> tagCounts(List<Trade> trades) {
        throw new UnsupportedOperationException();
    }
}
