package org.kata.orderbook;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Order(UUID id, Side side, BigDecimal price, int qty, Instant submittedAt) {
    public Order withQty(int newQty){
        return new Order(id, side, price, newQty, submittedAt);
    }
}
