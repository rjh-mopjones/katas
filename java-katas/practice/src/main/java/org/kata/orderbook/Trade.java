package org.kata.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Trade(UUID buyOrderId, UUID sellOrderId, int qty, BigDecimal price, Instant at) {
}
