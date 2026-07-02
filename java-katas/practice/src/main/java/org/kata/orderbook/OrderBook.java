package org.kata.orderbook;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class OrderBook {
    private final NavigableMap<BigDecimal, Deque<Order>> buys = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, Deque<Order>> sells = new TreeMap<>();
    private final Map<UUID, Order> openOrders = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Clock clock;

    public OrderBook(){
        this.clock = Clock.systemUTC();
    }
    public OrderBook(Clock clock){
        this.clock = clock;
    }
    public List<Trade> submit(Order order){
        return Collections.emptyList();
    }

    public boolean cancel(UUID orderId){
        return false;
    }

    public Optional<BigDecimal> bestBid(){
        return Optional.empty();
    }

    public Optional<BigDecimal> bestAsk(){
        return Optional.empty();
    }


}
