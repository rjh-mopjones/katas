package org.kata.cache;

import java.util.Optional;

/**
 * Generic read-through cache contract.
 *
 * <p>A cache sits in front of a slower backing store and answers lookups from a fast, bounded
 * in-memory store. When the fast store is full and a new entry must be admitted, one existing
 * entry is <em>evicted</em> to make room. The choice of <em>which</em> entry to evict is the
 * eviction policy, and it is the central design variable that distinguishes cache implementations.
 *
 * <h2>Eviction policy trade-offs</h2>
 *
 * <table border="1" cellpadding="4">
 *   <tr><th>Policy</th><th>Core idea</th><th>Time complexity</th><th>Hit-rate strength</th><th>Weakness</th><th>Used in</th></tr>
 *   <tr>
 *     <td><b>LRU</b> (Least Recently Used)</td>
 *     <td>Evict the entry that was least recently accessed.</td>
 *     <td>O(1) get &amp; put — HashMap + doubly-linked list</td>
 *     <td>Excellent for temporal locality (recently accessed items are likely to be needed again)</td>
 *     <td>Scan-resistance: a single sequential scan of a large dataset floods the cache and
 *         evicts all hot entries ("cache pollution"). The scan is never reused but the hot
 *         working set is gone.</td>
 *     <td>CPU L1/L2/L3 caches (hardware approximation), OS page cache, Redis with
 *         {@code allkeys-lru}, Guava {@code CacheBuilder.maximumSize}</td>
 *   </tr>
 *   <tr>
 *     <td><b>LFU</b> (Least Frequently Used)</td>
 *     <td>Evict the entry with the lowest access count; break ties by recency.</td>
 *     <td>O(1) get &amp; put — frequency-bucket design (see {@link LfuCache})</td>
 *     <td>Excellent for stable hot sets; rare keys are evicted before frequent ones</td>
 *     <td><em>Frequency bias</em>: a key that was hot a long time ago but is now stale retains
 *         a high count and blocks eviction of fresher (but lower-count) entries. Pure LFU
 *         "never forgets" without an aging mechanism.</td>
 *     <td>CDN edge caches, DNS caches, database buffer pools with stable hot spots</td>
 *   </tr>
 *   <tr>
 *     <td><b>FIFO</b> (First In, First Out)</td>
 *     <td>Evict the entry that was inserted earliest, regardless of access.</td>
 *     <td>O(1) — just a queue pointer advance</td>
 *     <td>Fine when all items have equal expected future access probability</td>
 *     <td>Ignores actual usage: a heavily-accessed entry is evicted just because it's old.
 *         In practice, hit rates are worse than LRU on almost every real workload.</td>
 *     <td>Simple hardware TLBs, some proxy caches where implementation simplicity beats
 *         optimality</td>
 *   </tr>
 *   <tr>
 *     <td><b>Random</b></td>
 *     <td>Pick a victim uniformly at random.</td>
 *     <td>O(1) — random array index</td>
 *     <td>Surprisingly competitive: avoids worst-case thrash; roughly matches LRU on random
 *         access patterns</td>
 *     <td>Unpredictable; can evict the hottest entry by chance.</td>
 *     <td>Redis {@code allkeys-random}, simple embedded caches where predictability is more
 *         important than optimal hit rate</td>
 *   </tr>
 *   <tr>
 *     <td><b>TTL</b> (Time To Live)</td>
 *     <td>Expire entries after a fixed wall-clock duration, independent of access frequency.</td>
 *     <td>O(log n) if backed by a priority queue; O(1) amortised with lazy expiry</td>
 *     <td>Essential for correctness when cached data becomes stale (auth tokens, DNS records,
 *         prices)</td>
 *     <td>Doesn't model access frequency at all; hot entries expire even if constantly used
 *         unless TTL is refreshed on access (sliding expiry).</td>
 *     <td>HTTP response caches (Cache-Control: max-age), Redis {@code EXPIRE}, Caffeine
 *         {@code expireAfterWrite}</td>
 *   </tr>
 * </table>
 *
 * <h2>Why pure LRU and pure LFU each have pathologies in production</h2>
 *
 * <p><b>LRU + scan = disaster.</b> A single range-scan query (e.g. a reporting job reading the
 * whole users table) issues thousands of cache fills for data that will never be re-read.
 * Because LRU recency wins, these scan entries displace the entire hot working set.
 * The fix — used by MySQL's InnoDB buffer pool, for example — is to split the LRU list into
 * a hot (young) and cold (old) sublist: new entries enter the cold half, and only graduate
 * to the hot half if re-accessed within a time window (the midpoint insertion strategy).
 *
 * <p><b>LFU + stable workload = frequency pollution.</b> A key that was queried a million
 * times yesterday has a count of 1 000 000. If it's now cold, it still won't be evicted
 * until every newer hotter key accumulates more than 1 000 000 hits. Without aging, LFU
 * caches gradually fill with historically-hot-but-currently-cold entries.
 *
 * <h2>The production answer: W-TinyLFU (Caffeine)</h2>
 *
 * <p>Caffeine's W-TinyLFU combines a small admission window (recency, like LRU) with a main
 * segment gated by a count-min-sketch frequency estimator (frequency, like LFU), plus a
 * periodic "decay" step that halves all counters on overflow — naturally implementing aging.
 * It achieves near-optimal hit rates on both scan-heavy and frequency-heavy workloads, with
 * O(1) amortised operations. In any senior interview, mentioning Caffeine and W-TinyLFU
 * demonstrates awareness of the real-world state of the art.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Implementations in this package are single-threaded unless otherwise specified (see
 * {@link ConcurrentLruCache} for a thread-safe variant). Single-threaded caches are appropriate
 * for unit tests, single-actor systems, and as the inner delegate of a concurrent wrapper.
 *
 * @param <K> key type; must satisfy the {@link Object#equals(Object)} / {@link Object#hashCode()}
 *            contract because keys are stored in a {@link java.util.HashMap}
 * @param <V> value type; may be any reference type
 */
public interface Cache<K, V> {

    /**
     * Looks up {@code key} and returns the associated value, or {@link Optional#empty()} on a
     * cache miss. A miss does <em>not</em> fetch from the backing store — that is the caller's
     * responsibility (look-aside / cache-aside pattern). Implementations that support eviction
     * policies based on recency (LRU) must <em>promote</em> the accessed key on a hit.
     *
     * @param key must not be null
     * @return the cached value, or empty if not present (or expired, in TTL implementations)
     */
    Optional<V> get(K key);

    /**
     * Inserts or updates the mapping for {@code key}. If the cache is at capacity and
     * {@code key} is not already present, one entry is evicted according to the implementation's
     * eviction policy before the new entry is stored. Updating an existing key (same key, new
     * value) must not trigger eviction and must not change the cache size.
     *
     * @param key   must not be null
     * @param value must not be null
     */
    void put(K key, V value);

    /**
     * Returns the number of entries currently in the cache, in the range {@code [0, capacity]}.
     * In a TTL cache this may transiently exceed the logical live count if lazy expiry is used —
     * the documentation of the concrete class clarifies the exact semantics.
     */
    int size();

    /**
     * Removes all entries. After this call, {@link #size()} returns 0 and every subsequent
     * {@link #get} returns empty. Useful in tests and for graceful shutdown sequences.
     */
    void clear();
}
