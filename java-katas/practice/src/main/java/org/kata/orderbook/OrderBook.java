package org.kata.orderbook;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class OrderBook {

    public OrderBook() {
        throw new UnsupportedOperationException();
    }

    public OrderBook(Clock clock) {
        throw new UnsupportedOperationException();
    }

    public List<Trade> submit(Order order) {
        throw new UnsupportedOperationException();
    }

    public boolean cancel(UUID orderId) {
        throw new UnsupportedOperationException();
    }

    public Optional<BigDecimal> bestBid() {
        throw new UnsupportedOperationException();
    }

    public Optional<BigDecimal> bestAsk() {
        throw new UnsupportedOperationException();
    }
}
