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

/**
 * Continuous limit order book with <b>price-time priority</b> matching.
 *
 * <h2>Matching model</h2>
 * Within a price level, orders match in FIFO order of submission — this is the
 * industry-standard rule on equity and futures exchanges. Across price levels, an
 * aggressing order always crosses the <i>best</i> opposite price first (highest bid
 * for a sell, lowest ask for a buy) and walks deeper into the book only as inventory
 * at the top level is exhausted. Trades print at the <i>resting</i> order's price,
 * giving the aggressor <b>price improvement</b>: they offered to pay more (or accept
 * less) than they ultimately had to.
 *
 * <h2>Data structures</h2>
 * <ul>
 *   <li>{@code buys}: {@link TreeMap} with {@link Comparator#reverseOrder()} so
 *       {@code firstEntry()} returns the highest bid — the best price for a seller
 *       to hit.</li>
 *   <li>{@code sells}: natural-ordered {@link TreeMap} so {@code firstEntry()} returns
 *       the lowest ask — the best price for a buyer to lift.</li>
 *   <li>Each price-level value is an {@link ArrayDeque} of {@link Order}, preserving
 *       arrival order. {@code peek}/{@code poll} at the head give us the time-priority
 *       winner in O(1).</li>
 *   <li>{@code openOrders}: flat {@code UUID → Order} index. The deque-of-orders shape
 *       is great for matching but O(n) to find by id; this map makes
 *       {@link #cancel(UUID)} O(log p) where p is the number of price levels.</li>
 * </ul>
 *
 * <h2>Concurrency: single-writer principle</h2>
 * All mutating operations are serialised on a single {@link ReentrantLock}. Matching
 * is inherently sequential — each fill changes book state, and the very next decision
 * (does the aggressor cross the new best price?) depends on that change. Fine-grained
 * locking per price level <i>adds</i> contention here rather than removing it because
 * a single aggressor routinely walks multiple levels. The reference implementation of
 * this pattern at scale is the <a href="https://lmax-exchange.github.io/disruptor/">
 * LMAX Disruptor</a>: a single producer / single consumer ring buffer that processes
 * orders sequentially on one thread and achieves millions of ops/sec by being
 * lock-free and cache-friendly. Parallelism, when needed, is achieved by sharding
 * across symbols, not by parallelising the match loop for a single symbol.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>IOC (Immediate-Or-Cancel):</b> fill what crosses now, discard the residual
 *       instead of resting it.</li>
 *   <li><b>FOK (Fill-Or-Kill):</b> pre-walk the opposite book; if the full qty can't
 *       be filled, reject the order entirely without producing partial fills.</li>
 *   <li><b>Market orders:</b> no price limit — match against best available until the
 *       qty is exhausted (and typically cancel any unfilled residual).</li>
 *   <li><b>Iceberg orders:</b> only a small "display" qty is visible at the level; on
 *       fill, a fresh slice is refreshed from the hidden reserve, losing time
 *       priority each refresh.</li>
 *   <li><b>Self-trade prevention:</b> reject (or cancel oldest) when both sides of a
 *       potential match belong to the same account.</li>
 *   <li><b>Pro-rata matching:</b> alternative to price-time priority used on some
 *       futures (e.g. short-end rates). At a level, fills are allocated proportional
 *       to resting qty rather than first-come-first-served.</li>
 * </ul>
 */
public class OrderBook {

    // Bids: best (highest) price first via reverse-ordered keys.
    private final NavigableMap<BigDecimal, Deque<Order>> buys = new TreeMap<>(Comparator.reverseOrder());
    // Asks: best (lowest) price first via natural ordering.
    private final NavigableMap<BigDecimal, Deque<Order>> sells = new TreeMap<>();
    // Flat id → order index for O(log p) cancellation. Mirrors what's in the deques.
    private final Map<UUID, Order> openOrders = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    // Clock is injected so tests can drive deterministic timestamps. Real exchanges
    // stamp trades to microsecond precision; the clock abstraction lets us swap in
    // a fixed clock for assertions without touching production timing semantics.
    private final Clock clock;

    public OrderBook() {
        this(Clock.systemUTC());
    }

    public OrderBook(Clock clock) {
        this.clock = clock;
    }

    /**
     * Submit an order. Matches against the opposite book as far as it crosses, rests
     * any residual at its limit price, and returns the trades produced (in order).
     */
    public List<Trade> submit(Order order) {
        // Stamp the submission instant BEFORE acquiring the lock. Two reasons:
        //   1. Keeps the critical section as short as possible — clock calls can be
        //      surprisingly expensive and we don't want them holding off other
        //      submitters.
        //   2. Matches the exchange convention of timestamping at submission/receipt,
        //      not at the moment each individual fill is internally booked. All
        //      trades produced by this aggressor share one timestamp.
        Instant now = clock.instant();
        lock.lock();
        try {
            List<Trade> trades = new ArrayList<>();
            int remainingQty = match(order, trades, now);
            // Anything left over after matching becomes a resting passive order.
            // (To support IOC, skip this rest() call.)
            if (remainingQty > 0) rest(order.withQty(remainingQty));
            return trades;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancel a resting order by id. Returns true if it was open and removed, false if
     * it was already filled, cancelled, or never existed (the caller can't tell which
     * — same as most exchange semantics for "unknown order id").
     */
    public boolean cancel(UUID orderId) {
        lock.lock();
        try {
            // Use the flat index to find the order without scanning every level.
            Order o = openOrders.remove(orderId);
            if (o == null) return false;
            NavigableMap<BigDecimal, Deque<Order>> book = o.side() == Side.BUY ? buys : sells;
            Deque<Order> level = book.get(o.price());
            if (level != null) {
                // O(n) within this price level only — acceptable because levels are
                // typically shallow, and cancel is rarer than match on most books.
                level.removeIf(x -> x.id().equals(orderId));
                // Don't leave empty levels behind: they pollute firstEntry() lookups
                // and inflate the TreeMap's height.
                if (level.isEmpty()) book.remove(o.price());
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** Highest resting bid, or empty if no buyers. */
    public Optional<BigDecimal> bestBid() {
        lock.lock();
        try { return buys.isEmpty() ? Optional.empty() : Optional.of(buys.firstKey()); }
        finally { lock.unlock(); }
    }

    /** Lowest resting ask, or empty if no sellers. */
    public Optional<BigDecimal> bestAsk() {
        lock.lock();
        try { return sells.isEmpty() ? Optional.empty() : Optional.of(sells.firstKey()); }
        finally { lock.unlock(); }
    }

    /**
     * Core matching loop. Walks the opposite book from best to worst price, consuming
     * resting orders FIFO at each level until either the aggressor is exhausted or
     * the next level no longer crosses.
     *
     * @return the aggressor's unfilled remainder (0 if fully consumed)
     */
    private int match(Order incoming, List<Trade> trades, Instant now) {
        // A BUY aggressor lifts offers from the sell side; a SELL aggressor hits bids.
        NavigableMap<BigDecimal, Deque<Order>> oppositeBook = incoming.side() == Side.BUY ? sells : buys;
        int remainingQty = incoming.qty();

        // Outer loop: iterate price levels of the opposite book from best to worst.
        // Both books are ordered so firstEntry() is always the best price available
        // to the aggressor — no scanning needed.
        while (remainingQty > 0 && !oppositeBook.isEmpty()) {
            Map.Entry<BigDecimal, Deque<Order>> bestLevel = oppositeBook.firstEntry();
            BigDecimal bestPrice = bestLevel.getKey();

            // Cross check: does the aggressor's limit reach this resting price?
            //   BUY  crosses when its bid  >= resting ask
            //   SELL crosses when its ask  <= resting bid
            // The moment we fail to cross at the best level, no deeper level can
            // cross either (prices only get worse), so we stop walking.
            boolean crosses = incoming.side() == Side.BUY
                    ? incoming.price().compareTo(bestPrice) >= 0
                    : incoming.price().compareTo(bestPrice) <= 0;
            if (!crosses) break;

            // Inner loop: drain the FIFO queue at this price level. The head of the
            // deque is the time-priority winner — earliest submission at this price.
            Deque<Order> queue = bestLevel.getValue();
            while (remainingQty > 0 && !queue.isEmpty()) {
                Order resting = queue.peek();
                // Trade qty is the smaller of what the aggressor still wants and what
                // this resting order has left to give. This is what makes partial
                // fills natural: one aggressor can fully consume several resting
                // orders and then partially consume the next one.
                int tradeQty = Math.min(remainingQty, resting.qty());

                // Identify buyer vs seller for the trade record. The aggressor is on
                // one side, the resting order on the other.
                UUID buyId = incoming.side() == Side.BUY ? incoming.id() : resting.id();
                UUID sellId = incoming.side() == Side.SELL ? incoming.id() : resting.id();
                // Trade prints at the RESTING price (price improvement for aggressor).
                trades.add(new Trade(buyId, sellId, tradeQty, bestPrice, now));

                remainingQty -= tradeQty;
                if (resting.qty() == tradeQty) {
                    // Resting order fully consumed: drop from the level and the
                    // flat index. Aggressor continues to the next resting order
                    // (or next level) if it still has qty.
                    queue.poll();
                    openOrders.remove(resting.id());
                } else {
                    // Resting order partially consumed: shrink its qty but keep its
                    // place at the head of the FIFO queue (time priority preserved
                    // — only the displayed quantity changes). Because Order is
                    // immutable we replace the instance via withQty().
                    Order updated = resting.withQty(resting.qty() - tradeQty);
                    queue.removeFirst();
                    queue.addFirst(updated);
                    openOrders.put(updated.id(), updated);
                    // Note: when we partially fill a resting order, the aggressor's
                    // remaining qty is necessarily 0, so the inner loop will exit.
                }
            }
            // Clean up empty price levels so firstEntry() doesn't keep returning a
            // level with no liquidity, and so the TreeMap stays compact.
            if (queue.isEmpty()) oppositeBook.remove(bestPrice);
        }
        return remainingQty;
    }

    /**
     * Park an unfilled (or partially filled) order on its own side of the book at its
     * limit price, joining the back of the FIFO queue at that level.
     */
    private void rest(Order order) {
        NavigableMap<BigDecimal, Deque<Order>> book = order.side() == Side.BUY ? buys : sells;
        // computeIfAbsent lazily creates the level deque on first touch — cheaper
        // than pre-seeding every possible price.
        book.computeIfAbsent(order.price(), k -> new ArrayDeque<>()).add(order);
        openOrders.put(order.id(), order);
    }
}
