package org.kata.quotecache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A cache in which every entry expires a fixed duration after it was <em>written</em> —
 * stale-price protection for a trading desk. A quote that hasn't been refreshed inside the TTL is
 * worse than no quote at all: trading against it risks execution at a price nobody would honour.
 *
 * <p>Time is a {@link LongSupplier} of nanoseconds (not {@code System.nanoTime()} inline) so tests
 * can drive it with an {@link java.util.concurrent.atomic.AtomicLong}.
 *
 * <h2>Time-based eviction, not LRU/LFU</h2>
 * This is a deliberately different eviction policy from an {@code LruCache}/{@code LfuCache}:
 * those evict based on <em>recency</em> or <em>frequency</em> of access once the cache is at
 * capacity — a hot entry survives indefinitely. A {@code QuoteCache} has no capacity bound at all;
 * every entry evicts on a wall-clock deadline regardless of how often it is read. A quote read a
 * thousand times a second is exactly as stale at {@code ttlNanos} as one never read again — access
 * pattern is irrelevant to whether a price is safe to trade on.
 *
 * <h2>Expiry boundary</h2>
 * An entry stored at time {@code t} is expired at query time {@code now} iff
 * {@code now - t >= ttlNanos}. The boundary is <b>inclusive</b> of expiry: an entry exactly
 * {@code ttlNanos} old is already stale, not "just barely fresh". This mirrors "quotes are valid
 * for up to N nanos" rather than "quotes are valid for more than N nanos" — the stricter reading is
 * the safe one for a price feed.
 *
 * <h2>Lazy expiry</h2>
 * There is no background reaper. Every accessor ({@link #get}, {@link #size}, {@link #containsKey})
 * checks the entry's age against {@code ttlNanos} and removes it on the spot if stale. This keeps
 * {@link #put} O(1) with zero bookkeeping overhead — no timer wheel, no priority queue to maintain
 * on every write — at the cost of letting dead entries linger in the map (and count toward memory)
 * until something touches that exact key again. For a quote cache this trade is usually right: hot
 * symbols get re-quoted constantly, so lazy touch keeps them pruned in practice; a cold, abandoned
 * symbol wastes a little memory until evicted by whatever process eventually looks it up (or never,
 * if nothing ever does — see "Possible extensions" for a fix).
 *
 * <h2>Concurrency</h2>
 * Backed by a {@link ConcurrentHashMap}, so concurrent {@code put}/{@code get} from many threads
 * needs no external locking. The one subtlety is in {@link #get}: expiring an entry must not race
 * a concurrent {@link #put} that just refreshed the same key. Removing the expired entry uses the
 * conditional {@link ConcurrentHashMap#remove(Object, Object)} (remove-if-still-mapped-to-this-
 * exact-entry) rather than an unconditional {@code remove(key)}, so a reader that observed a stale
 * snapshot can never delete a fresher write it raced past.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Active reaper</b> — a {@link java.util.concurrent.ScheduledExecutorService} or a
 *       {@link java.util.concurrent.DelayQueue} of keys ordered by deadline, drained periodically,
 *       so memory is reclaimed even for keys nobody ever reads again.</li>
 *   <li><b>Per-entry TTL</b> — accept an optional TTL override on {@link #put} instead of one fixed
 *       {@code ttlNanos} for the whole cache, for symbols with different quote-freshness SLAs.</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class QuoteCache<K, V> {

    private record Entry<V>(V value, long storedAt) {
    }

    private final long ttlNanos;
    private final LongSupplier clock;
    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();

    /** Creates a cache with the given TTL, using the system nanosecond clock. */
    public QuoteCache(long ttlNanos) {
        this(ttlNanos, System::nanoTime);
    }

    /**
     * @param ttlNanos how long (in nanoseconds) an entry stays live after it is written; must be
     *                 positive
     * @param clock    supplies the current time in nanoseconds; injected for testability
     * @throws IllegalArgumentException if {@code ttlNanos <= 0}
     */
    public QuoteCache(long ttlNanos, LongSupplier clock) {
        if (ttlNanos <= 0) {
            throw new IllegalArgumentException("ttlNanos must be positive: " + ttlNanos);
        }
        this.ttlNanos = ttlNanos;
        this.clock = clock;
    }

    /** Store (or overwrite) {@code key}, stamping the current time. Overwriting resets the TTL. */
    public void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.getAsLong()));
    }

    /**
     * @return the value for {@code key} if present and not expired, else {@link Optional#empty()}.
     * An expired entry is removed as a side effect of this call (lazy purge).
     */
    public Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry, clock.getAsLong())) {
            // Conditional remove: only delete if the map still holds this exact (now-expired)
            // entry. If a concurrent put() already replaced it with a fresh one, that fresh
            // entry must survive — we'd otherwise race-delete a live write.
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /** Number of live (non-expired) entries; expired entries are purged lazily as they are found. */
    public int size() {
        long now = clock.getAsLong();
        int live = 0;
        for (var mapEntry : entries.entrySet()) {
            Entry<V> entry = mapEntry.getValue();
            if (isExpired(entry, now)) {
                entries.remove(mapEntry.getKey(), entry);
            } else {
                live++;
            }
        }
        return live;
    }

    /** True iff a live (non-expired) entry exists for {@code key}. */
    public boolean containsKey(K key) {
        return get(key).isPresent();
    }

    private boolean isExpired(Entry<V> entry, long now) {
        return now - entry.storedAt() >= ttlNanos;
    }
}
