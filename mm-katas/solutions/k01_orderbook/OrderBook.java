package com.katas.k01_orderbook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Worked reference — passes Stage 1 through Stage 4. See NOTES.md for the pivot each stage forces.
 *
 * <p>Design: two {@link TreeMap}s keyed by price (bids descending so {@code firstKey} is the best bid;
 * asks ascending so {@code firstKey} is the best ask), each value an FIFO {@link ArrayDeque} of resting
 * orders at that price. A {@link HashMap} from id to the resting node makes cancel O(1) to find. A
 * single {@link ReentrantLock} serialises the (inherently sequential) match loop — Stage 4.
 */
public final class OrderBook {

    /** Mutable resting-order node: {@code remaining} shrinks as it is filled. */
    private static final class Resting {
        final long id;
        final String account;
        final Side side;
        final long price;
        long remaining;

        Resting(long id, String account, Side side, long price, long remaining) {
            this.id = id;
            this.account = account;
            this.side = side;
            this.price = price;
            this.remaining = remaining;
        }
    }

    private final NavigableMap<Long, ArrayDeque<Resting>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, ArrayDeque<Resting>> asks = new TreeMap<>();
    private final Map<Long, Resting> byId = new HashMap<>();
    private final StpPolicy stp;
    private final ReentrantLock lock = new ReentrantLock();

    public OrderBook() {
        this(StpPolicy.NONE);
    }

    public OrderBook(StpPolicy stpPolicy) {
        this.stp = stpPolicy;
    }

    public List<Fill> submit(Order order) {
        lock.lock();
        try {
            List<Fill> fills = new ArrayList<>();
            long remaining = match(order, fills);
            if (remaining > 0 && order.type() == OrderType.LIMIT) {
                rest(new Resting(order.id(), order.account(), order.side(), order.price(), remaining));
            }
            return fills;
        } finally {
            lock.unlock();
        }
    }

    /** Match {@code order} against the opposite book, appending fills; returns its unmatched remainder. */
    private long match(Order order, List<Fill> fills) {
        long remaining = order.qty();
        NavigableMap<Long, ArrayDeque<Resting>> opposite = (order.side() == Side.BUY) ? asks : bids;

        while (remaining > 0 && !opposite.isEmpty()) {
            Map.Entry<Long, ArrayDeque<Resting>> best = opposite.firstEntry();
            long price = best.getKey();
            if (order.type() == OrderType.LIMIT && !crosses(order.side(), order.price(), price)) {
                break; // not marketable — the rest of the book is worse
            }
            ArrayDeque<Resting> level = best.getValue();
            Resting maker = level.peekFirst();

            if (stp != StpPolicy.NONE && maker.account.equals(order.account())) {
                if (stp == StpPolicy.CANCEL_NEWEST) {
                    return 0; // drop the taker's remainder (prior fills stand, nothing rests)
                }
                removeHead(level, price, maker, opposite); // CANCEL_RESTING: drop maker, retry level
                continue;
            }

            long tradeQty = Math.min(remaining, maker.remaining);
            fills.add(new Fill(maker.id, order.id(), price, tradeQty));
            remaining -= tradeQty;
            maker.remaining -= tradeQty;
            if (maker.remaining == 0) {
                removeHead(level, price, maker, opposite);
            }
        }
        return remaining;
    }

    private static boolean crosses(Side takerSide, long takerPrice, long bestOppositePrice) {
        return takerSide == Side.BUY ? takerPrice >= bestOppositePrice : takerPrice <= bestOppositePrice;
    }

    private void removeHead(ArrayDeque<Resting> level, long price, Resting head,
                            NavigableMap<Long, ArrayDeque<Resting>> book) {
        level.pollFirst();
        byId.remove(head.id);
        if (level.isEmpty()) {
            book.remove(price);
        }
    }

    private void rest(Resting r) {
        NavigableMap<Long, ArrayDeque<Resting>> book = (r.side == Side.BUY) ? bids : asks;
        book.computeIfAbsent(r.price, p -> new ArrayDeque<>()).addLast(r);
        byId.put(r.id, r);
    }

    public boolean cancel(long orderId) {
        lock.lock();
        try {
            Resting r = byId.remove(orderId);
            if (r == null) {
                return false;
            }
            NavigableMap<Long, ArrayDeque<Resting>> book = (r.side == Side.BUY) ? bids : asks;
            ArrayDeque<Resting> level = book.get(r.price);
            if (level != null) {
                level.remove(r); // identity removal (O(level size))
                if (level.isEmpty()) {
                    book.remove(r.price);
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public OptionalLong bestBid() {
        lock.lock();
        try {
            return bids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(bids.firstKey());
        } finally {
            lock.unlock();
        }
    }

    public OptionalLong bestAsk() {
        lock.lock();
        try {
            return asks.isEmpty() ? OptionalLong.empty() : OptionalLong.of(asks.firstKey());
        } finally {
            lock.unlock();
        }
    }
}
