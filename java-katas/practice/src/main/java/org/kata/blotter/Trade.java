package org.kata.blotter;

import java.math.BigDecimal;
import java.util.List;

public record Trade(String desk, String symbol, Side side, BigDecimal pnl, List<String> tags) {
}
