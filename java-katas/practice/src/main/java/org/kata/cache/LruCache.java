package org.kata.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Single-threaded LRU (Least Recently Used) cache with O(1) {@code get} and {@code put}.
 *
 * <h2>The classic data-structure combination</h2>
 *
 * <p>The O(1) guarantee requires two cooperating structures:
 * <ol>
 *   <li><b>{@link HashMap}&lt;K, Node&gt;</b> — gives O(1) average lookup: from a key we jump
 *       directly to the doubly-linked-list node that holds both the value and the list pointers.
 *       Without the map, finding a node in the list would take O(n) time, making every
 *       "move to front" operation linear.</li>
 *   <li><b>Intrusive doubly-linked list</b> — maintains the recency order. Head is the
 *       most-recently-used entry; tail is the least-recently-used entry and therefore the
 *       eviction candidate. The list is <em>intrusive</em>: instead of wrapping nodes in a
 *       separate list node, each cache node carries its own {@code prev} and {@code next}
 *       references. This avoids a second allocation per entry and, more importantly, allows
 *       O(1) <em>unlink</em> from any interior position — crucial for promoting a hit entry
 *       to the front. A singly-linked list cannot unlink an arbitrary interior node in O(1)
 *       because finding the predecessor requires O(n) traversal.</li>
 * </ol>
 *
 * <h2>Why not {@code LinkedHashMap}?</h2>
 *
 * <p>{@link java.util.LinkedHashMap} with {@code accessOrder=true} and an overridden
 * {@link java.util.LinkedHashMap#removeEldestEntry} is the idiomatic production shortcut:
 * three lines of code, well-tested, and zero maintenance burden. <strong>In a production
 * codebase, always prefer it.</strong> This hand-rolled version exists because interviewers
 * want to see that you understand the mechanics: "how would you implement LRU from scratch?"
 * is one of the most common system-design / coding-round questions. Knowing the underlying
 * structure demonstrates you understand not just what to reach for but why it works.
 *
 * <pre>{@code
 * // Production shortcut (LinkedHashMap):
 * new LinkedHashMap<K,V>(capacity, 0.75f, true) {
 *     protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
 *         return size() > capacity;
 *     }
 * };
 * }</pre>
 *
 * <h2>Sentinel-node trick</h2>
 *
 * <p>This implementation uses a {@code head} sentinel (most-recent end) and a {@code tail}
 * sentinel (least-recent end). Sentinels are empty, permanently-present dummy nodes. They
 * eliminate all the null-pointer edge cases for insert/remove at the list boundaries: every
 * real node always has non-null {@code prev} and {@code next}, which simplifies the pointer
 * manipulation from four cases down to one.
 *
 * <h2>Thread safety</h2>
 *
 * <p>This class is <b>not thread-safe</b>. See {@link ConcurrentLruCache} for a thread-safe
 * wrapper. Using this class from multiple threads without external synchronisation will
 * produce data races that corrupt the linked list.
 *
 * @param <K> key type; must satisfy the equals/hashCode contract
 * @param <V> value type
 */
public class LruCache<K, V> implements Cache<K, V> {

    /**
     * Doubly-linked list node. Carries the key (needed during eviction to remove the
     * corresponding entry from the HashMap — without the key, we'd have to scan the map),
     * the value, and the prev/next list pointers.
     */
    private class Node {
        K key;
        V value;
        Node prev;
        Node next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }

        /** Sentinel constructor — key and value are irrelevant. */
        Node() {}
    }

    private final int capacity;

    /**
     * Map from key to the list node holding that key. O(1) lookup means we can jump straight
     * to the node and promote it to the front without scanning the list.
     */
    private final Map<K, Node> map;

    /**
     * Permanent sentinel for the most-recently-used end of the list. Real nodes are inserted
     * immediately after head: {@code head <-> newest <-> ... <-> oldest <-> tail}.
     */
    private final Node head = new Node();

    /**
     * Permanent sentinel for the least-recently-used (eviction candidate) end of the list.
     * When the cache is full, the node immediately before {@code tail} is evicted.
     */
    private final Node tail = new Node();

    /**
     * Creates an LRU cache with the given capacity.
     *
     * @param capacity maximum number of entries; must be at least 1
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public LruCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
        // Pre-size the HashMap to avoid rehashing. The load factor 0.75 is the default;
        // dividing by it gives us the smallest initial table that won't rehash before
        // we've filled the cache to capacity.
        this.map = new HashMap<>((int) (capacity / 0.75f) + 1);

        // Wire up the two sentinels: the list starts empty but the boundaries are fixed.
        head.next = tail;
        tail.prev = head;
    }

    /**
     * Returns the value associated with {@code key}, or empty on a miss.
     *
     * <p>On a hit, the node is <em>promoted</em> to the most-recently-used position
     * (immediately after {@code head}). Promotion is the critical LRU invariant: after
     * every access, the accessed entry is last in line for eviction.
     *
     * <p>Time: O(1) amortised — one HashMap lookup, then O(1) list operations (unlink + insert).
     */
    @Override
    public Optional<V> get(K key) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Inserts or updates the mapping for {@code key}.
     *
     * <ul>
     *   <li><b>Update (key exists):</b> replace the value in the existing node and promote it
     *       to most-recent. Size is unchanged — no eviction occurs.</li>
     *   <li><b>Insert (new key, cache not full):</b> create a new node, add to map, insert at
     *       front.</li>
     *   <li><b>Insert (new key, cache full):</b> evict the node immediately before {@code tail}
     *       (the LRU entry), remove it from the map, then insert the new node at front.</li>
     * </ul>
     *
     * <p>Time: O(1) amortised.
     */
    @Override
    public void put(K key, V value) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Removes all entries and resets the linked list to its empty (sentinels-only) state.
     * Time: O(n) to clear the HashMap (GC work); the list is rewired in O(1).
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    // -------------------------------------------------------------------------
    // Private linked-list helpers
    // -------------------------------------------------------------------------

    /**
     * Unlinks {@code node} from its current position and re-inserts it immediately after
     * {@code head} (the MRU end). This is the O(1) "move to front" operation that makes
     * LRU work: both the unlink and the insert touch only a fixed number of pointer swaps
     * regardless of list length.
     */
    private void moveToFront(Node node) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Splices {@code node} out of the list by re-wiring its neighbours to bypass it.
     * After this call, {@code node.prev} and {@code node.next} still point at the old
     * neighbours — those are deliberately left stale because the caller immediately sets
     * them in {@link #insertAtFront}.
     */
    private void unlink(Node node) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Inserts {@code node} immediately after the {@code head} sentinel (MRU position).
     * Concretely: head <-> node <-> (what was head.next).
     */
    private void insertAtFront(Node node) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Removes the least-recently-used entry from both the list and the map. The LRU node is
     * always {@code tail.prev} (the node just before the tail sentinel). We need the node's
     * {@code key} to remove it from the HashMap — this is why nodes store their key.
     */
    private void evictLru() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
