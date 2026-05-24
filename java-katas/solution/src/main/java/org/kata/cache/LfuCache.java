package org.kata.cache;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

/**
 * Single-threaded LFU (Least Frequently Used) cache with O(1) {@code get} and {@code put}.
 *
 * <h2>Why LFU is harder than LRU</h2>
 *
 * <p>LRU only needs to track relative recency: a doubly-linked list gives a natural total order
 * over "how recently was this accessed?" LFU must track absolute frequency counts — and when
 * the cache is full, it needs to find the entry with the globally minimum count in O(1) time.
 * A naive approach (scan all entries for the minimum) is O(n) per eviction, which is too slow.
 *
 * <h2>The O(1) frequency-bucket algorithm</h2>
 *
 * <p>This is the algorithm from <em>O(1) LFU cache eviction algorithm</em> (Shah et al., 2010),
 * the standard answer expected in senior engineering interviews. The key insight:
 * instead of maintaining one sorted structure over all keys, maintain a map from
 * <em>frequency value</em> → <em>set of keys at that frequency</em>. When we need to evict,
 * we look up the set at {@code minFreq} (a maintained scalar) — O(1).
 *
 * <h2>Data structures</h2>
 *
 * <ul>
 *   <li>{@code keyToNode: Map<K, Node>} — maps each key to its node, which stores the value
 *       and current frequency count. O(1) lookup by key.</li>
 *   <li>{@code freqToKeys: Map<Integer, LinkedHashSet<K>>} — maps each frequency f to the
 *       ordered set of keys that have been accessed exactly f times. {@link LinkedHashSet}
 *       preserves insertion order, which gives us LRU tie-breaking within a frequency bucket
 *       for free: the first element in the set is the least-recently-promoted key at that
 *       frequency, so on eviction we remove the "first" element.</li>
 *   <li>{@code minFreq: int} — tracks the current minimum frequency in the cache. Maintained
 *       carefully so that looking up the eviction candidate is always O(1).</li>
 * </ul>
 *
 * <h2>minFreq maintenance — the subtle part</h2>
 *
 * <p>This is the tricky invariant to keep correct. The rules are:
 * <ol>
 *   <li><b>On {@code get}:</b> the accessed key moves from frequency {@code f} to {@code f+1}.
 *       If the old bucket {@code f} is now empty <em>and</em> {@code f == minFreq}, then
 *       {@code minFreq} must become {@code f+1}. (If {@code f > minFreq}, the minimum bucket
 *       still has other keys, so minFreq is unchanged.)</li>
 *   <li><b>On {@code put} (new key):</b> the new key always starts at frequency 1. Therefore
 *       {@code minFreq} is always set to 1. This is safe even if existing entries have higher
 *       frequencies — the new entry is by definition the least frequent.</li>
 *   <li><b>On {@code put} (existing key):</b> treat exactly like {@code get}: promote the
 *       key from its current frequency, applying the same minFreq update rule. minFreq is
 *       <em>not</em> reset to 1 here because we are not inserting a new entry.</li>
 *   <li><b>On eviction:</b> we evict from {@code freqToKeys.get(minFreq)}. After eviction,
 *       minFreq is about to be set to 1 (because a new entry is always inserted immediately
 *       after eviction in {@code put}), so we don't need to recompute it.</li>
 * </ol>
 *
 * <h2>LRU tie-breaking</h2>
 *
 * <p>When multiple keys share the minimum frequency, we evict the one that was least recently
 * used within that frequency group. {@link LinkedHashSet} maintains insertion order, so the
 * first key in the set is the "oldest" at that frequency level — i.e., the one that was
 * promoted to this frequency longest ago. Calling {@code iterator().next()} on the set gives
 * the LRU candidate in O(1).
 *
 * <h2>Thread safety</h2>
 *
 * <p>This class is <b>not thread-safe</b>. The three data structures must remain consistent
 * with each other; concurrent mutations without external synchronisation produce undefined
 * behaviour.
 *
 * @param <K> key type; must satisfy the equals/hashCode contract
 * @param <V> value type
 */
public class LfuCache<K, V> implements Cache<K, V> {

    /**
     * Per-key metadata node. Storing the frequency on the node (rather than in a separate
     * {@code Map<K,Integer>}) means a key promotion only touches one map: we look up the node,
     * read its current frequency (to find the old bucket), increment, and put into the new
     * bucket — no second map lookup needed for the count.
     */
    private static class Node<V> {
        V value;
        int freq;

        Node(V value) {
            this.value = value;
            this.freq = 1; // all new entries start at frequency 1
        }
    }

    private final int capacity;
    private final Map<K, Node<V>> keyToNode;

    /**
     * Maps frequency integer to the ordered set of keys at that frequency. Using
     * {@link LinkedHashSet} (not {@link java.util.HashSet}) is essential: the insertion
     * order tracks recency within the frequency bucket, enabling O(1) LRU tie-breaking.
     * A {@link java.util.TreeSet} would give the same order guarantee but O(log n) add/remove;
     * a plain array/deque per bucket would also work but LinkedHashSet has a clean API.
     */
    private final Map<Integer, LinkedHashSet<K>> freqToKeys;

    /**
     * The current minimum frequency among all cached entries. Maintained as a scalar so
     * the eviction candidate can be found in O(1): {@code freqToKeys.get(minFreq)}.
     */
    private int minFreq;

    /**
     * Creates an LFU cache with the given capacity.
     *
     * @param capacity maximum number of entries; must be at least 1
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public LfuCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
        this.keyToNode = new HashMap<>((int) (capacity / 0.75f) + 1);
        this.freqToKeys = new HashMap<>();
        this.minFreq = 0;
    }

    /**
     * Returns the value for {@code key}, or empty on a miss. On a hit, the key's frequency
     * count is incremented and it is moved into the next-higher frequency bucket.
     *
     * <p>Time: O(1) amortised — one map lookup, then O(1) bucket operations.
     */
    @Override
    public Optional<V> get(K key) {
        Node<V> node = keyToNode.get(key);
        if (node == null) return Optional.empty();

        // Promote the key: increment its frequency and move it to the new bucket.
        promoteKey(key, node);
        return Optional.of(node.value);
    }

    /**
     * Inserts or updates the mapping for {@code key}.
     *
     * <ul>
     *   <li><b>Update (key exists):</b> update the value and promote (like {@code get}).
     *       minFreq update follows the same rules as promotion.</li>
     *   <li><b>Insert (new key):</b> evict the LRU key at {@code minFreq} if at capacity,
     *       then insert the new key at frequency 1, resetting {@code minFreq = 1}.</li>
     * </ul>
     *
     * <p>Time: O(1) amortised.
     */
    @Override
    public void put(K key, V value) {
        Node<V> existing = keyToNode.get(key);

        if (existing != null) {
            // Update value in place, then promote exactly as get() would.
            existing.value = value;
            promoteKey(key, existing);
            return;
        }

        // New key. Evict if at capacity before inserting.
        if (keyToNode.size() == capacity) {
            evictLfu();
        }

        // Insert at frequency 1. New entries are, by definition, the least frequent,
        // so minFreq resets to 1 unconditionally.
        Node<V> node = new Node<>(value);
        keyToNode.put(key, node);
        freqToKeys.computeIfAbsent(1, f -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return keyToNode.size();
    }

    /**
     * Removes all entries and resets minFreq. Time: O(n) to clear maps.
     */
    @Override
    public void clear() {
        keyToNode.clear();
        freqToKeys.clear();
        minFreq = 0;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Promotes {@code key} (whose metadata is {@code node}) from its current frequency
     * {@code f} to frequency {@code f+1}.
     *
     * <p>Steps:
     * <ol>
     *   <li>Remove key from the bucket at {@code f}. If that bucket is now empty and
     *       {@code f == minFreq}, increment minFreq to {@code f+1} — the old minimum no
     *       longer has any entries.</li>
     *   <li>Increment the frequency stored on the node.</li>
     *   <li>Add key to the bucket at {@code f+1}, creating it if absent. Because
     *       {@link LinkedHashSet} preserves insertion order, this key is now the
     *       most-recently-used entry at frequency {@code f+1}.</li>
     * </ol>
     *
     * <p>Why we can safely increment minFreq by exactly 1 here (step 1): if the bucket at
     * {@code f} is now empty and {@code f == minFreq}, the next possible minimum is {@code f+1}
     * — the key we just promoted is there, so the bucket is non-empty. No other key can have
     * a frequency between {@code f} and {@code f+1} because frequencies are integers and they
     * only ever increment by 1.
     */
    private void promoteKey(K key, Node<V> node) {
        int oldFreq = node.freq;

        // Step 1: Remove from old bucket.
        LinkedHashSet<K> oldBucket = freqToKeys.get(oldFreq);
        oldBucket.remove(key);
        if (oldBucket.isEmpty()) {
            freqToKeys.remove(oldFreq);
            // If this was the minimum frequency bucket and it's now empty, the new minimum
            // is oldFreq+1 — we know that bucket will be non-empty after step 3.
            if (oldFreq == minFreq) {
                minFreq = oldFreq + 1;
            }
        }

        // Step 2: Increment frequency on node.
        node.freq = oldFreq + 1;

        // Step 3: Add to new bucket. The key is appended to the end of the LinkedHashSet,
        // making it the most-recently-used entry at this frequency.
        freqToKeys.computeIfAbsent(node.freq, f -> new LinkedHashSet<>()).add(key);
    }

    /**
     * Evicts the least-frequently-used key (and, on tie, the least-recently-used among them).
     *
     * <p>The eviction candidate is always the first element of {@code freqToKeys.get(minFreq)}.
     * {@link LinkedHashSet} iteration visits in insertion order: the first element is the key
     * that was added to (or promoted into) this bucket the longest ago — i.e., the LRU entry
     * within the minimum-frequency tier.
     *
     * <p>After removal, if the bucket is now empty we clean it up. We do <em>not</em> update
     * minFreq here: the caller always inserts a new key immediately after eviction, which sets
     * {@code minFreq = 1} — so any value we set now would be immediately overwritten.
     */
    private void evictLfu() {
        LinkedHashSet<K> minBucket = freqToKeys.get(minFreq);
        // Iterator over a LinkedHashSet visits in insertion order; the first is the LRU key.
        K evictKey = minBucket.iterator().next();
        minBucket.remove(evictKey);
        if (minBucket.isEmpty()) {
            freqToKeys.remove(minFreq);
        }
        keyToNode.remove(evictKey);
    }
}
