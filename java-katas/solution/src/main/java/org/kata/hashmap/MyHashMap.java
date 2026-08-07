package org.kata.hashmap;

import java.util.Objects;

/**
 * Hash table implementation of a key-value map, from first principles — the data structure behind
 * {@link java.util.HashMap}.
 *
 * <h2>Model</h2>
 * A mutable map from {@code K} to {@code V} supporting {@link #put}, {@link #get}, {@link #remove},
 * {@link #size()} and {@link #containsKey}. A {@code null} key is a valid key (it is not special-cased
 * away, it is deterministically routed to bucket {@code 0}). Key identity for lookups is
 * {@link Object#equals(Object)}, never reference equality, so two distinct-but-equal key instances
 * (e.g. two {@code new String("x")}) resolve to the same entry — the same contract
 * {@link java.util.HashMap} promises.
 *
 * <h2>Data structure</h2>
 * An array of bucket chains (<b>separate chaining</b>): {@code buckets[i]} is the head of a singly
 * linked list of {@link Node}s whose spread hash maps to index {@code i}. On {@link #put}, the key's
 * {@code hashCode()} is <em>spread</em> ({@code h ^ (h >>> 16)}, XOR-folding the high bits into the
 * low bits) before masking it down to an index with {@code hash & (capacity - 1)} — a single AND
 * instead of a modulo, which is only correct because capacity is kept a power of two on every resize.
 * Spreading matters because for many real hash functions (small {@code Integer}s, short {@code String}s)
 * the low bits alone carry most of the entropy; without folding, a small capacity would collide keys
 * that a modulo against a larger, non-power-of-two capacity would have separated. Past a <b>load
 * factor</b> of {@code 0.75} ({@code size > capacity * 0.75}), the table <b>doubles and rehashes</b>
 * every entry against the new capacity — moving existing {@link Node}s rather than reallocating them,
 * since a node's key/value never changes across a resize, only which bucket it lives in.
 *
 * <h2>Trade-offs</h2>
 * Chaining degrades gracefully — a bucket can hold arbitrarily many entries — at the cost of one
 * pointer per entry and pointer-chasing cache misses on long chains; a poor or adversarial hash
 * function collapses every key into one bucket and get/put degrade to O(n). The alternative,
 * <b>open addressing</b>, packs entries directly into the array (better cache locality, no per-entry
 * node allocation) but pays for it with more delicate deletion (tombstones) and clustering under high
 * load factor. Resize is O(n) but amortises to O(1) per {@code put} across the doublings, the same
 * argument as {@link java.util.ArrayList} growth.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Open addressing</b> — linear or quadratic probing with tombstone markers for deletion,
 *       trading chain-pointer overhead for cache locality.</li>
 *   <li><b>Treeify hot buckets</b> — once a single bucket's chain exceeds a threshold (as
 *       {@link java.util.HashMap} does at 8), convert that bucket to a red-black tree keyed by
 *       {@code hashCode()} so a pathological chain degrades to O(log n) instead of O(n).</li>
 * </ul>
 */
public final class MyHashMap<K, V> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final double LOAD_FACTOR = 0.75;

    /** A single link in a bucket chain; caches the spread hash so resize never re-derives it. */
    private static final class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private Node<K, V>[] buckets;
    private int size;
    private int threshold;

    /** Creates an empty map with the default initial capacity ({@value #DEFAULT_CAPACITY}). */
    public MyHashMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates an empty map whose bucket array has room for at least {@code initialCapacity} buckets,
     * rounded up to the next power of two.
     *
     * @throws IllegalArgumentException if {@code initialCapacity <= 0}
     */
    public MyHashMap(int initialCapacity) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive: " + initialCapacity);
        }
        int capacity = tableSizeFor(initialCapacity);
        this.buckets = newTable(capacity);
        this.threshold = (int) (capacity * LOAD_FACTOR);
    }

    // Generic array creation (`new Node<K,V>[n]`) is illegal in Java: type erasure means the JVM
    // has no reified K/V to check on array store, so the language forbids it outright. The standard
    // workaround (the one java.util.HashMap itself uses) is to allocate a RAW `Node[]` and cast to
    // `Node<K,V>[]` — the cast is unchecked but safe because this array is private and every element
    // we ever store is a Node<K,V> we constructed ourselves. (Casting a plain `Object[]` here would
    // fail at runtime: an Object[] is not a Node[], so the array-type cast throws ClassCastException.)
    @SuppressWarnings("unchecked")
    private Node<K, V>[] newTable(int capacity) {
        return (Node<K, V>[]) new Node[capacity];
    }

    /** Rounds {@code capacity} up to the next power of two (unchanged if already one). */
    private static int tableSizeFor(int capacity) {
        int n = capacity - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n < 0 ? 1 : n + 1;
    }

    /** Spreads a key's {@code hashCode()} by XOR-folding its high bits into its low bits. */
    private static int spread(Object key) {
        if (key == null) {
            return 0;
        }
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private static int indexFor(int hash, int capacity) {
        return hash & (capacity - 1);
    }

    /**
     * Inserts or overwrites {@code key}'s mapping.
     *
     * @return the previous value associated with {@code key}, or {@code null} if it was absent
     */
    public V put(K key, V value) {
        int hash = spread(key);
        int idx = indexFor(hash, buckets.length);
        for (Node<K, V> node = buckets[idx]; node != null; node = node.next) {
            if (node.hash == hash && Objects.equals(node.key, key)) {
                V previous = node.value;
                node.value = value;
                return previous;
            }
        }
        buckets[idx] = new Node<>(hash, key, value, buckets[idx]);
        size++;
        if (size > threshold) {
            resize();
        }
        return null;
    }

    /** @return the value mapped to {@code key}, or {@code null} if no such mapping exists */
    public V get(K key) {
        Node<K, V> node = findNode(key);
        return node == null ? null : node.value;
    }

    /** @return {@code true} if this map contains a mapping for {@code key} */
    public boolean containsKey(K key) {
        return findNode(key) != null;
    }

    /**
     * Removes the mapping for {@code key}, if present.
     *
     * @return the removed value, or {@code null} if {@code key} was not mapped
     */
    public V remove(K key) {
        int hash = spread(key);
        int idx = indexFor(hash, buckets.length);
        Node<K, V> prev = null;
        for (Node<K, V> node = buckets[idx]; node != null; node = node.next) {
            if (node.hash == hash && Objects.equals(node.key, key)) {
                if (prev == null) {
                    buckets[idx] = node.next;
                } else {
                    prev.next = node.next;
                }
                size--;
                return node.value;
            }
            prev = node;
        }
        return null;
    }

    /** @return the number of key-value mappings currently in this map */
    public int size() {
        return size;
    }

    private Node<K, V> findNode(K key) {
        int hash = spread(key);
        for (Node<K, V> node = buckets[indexFor(hash, buckets.length)]; node != null; node = node.next) {
            if (node.hash == hash && Objects.equals(node.key, key)) {
                return node;
            }
        }
        return null;
    }

    /**
     * Doubles the bucket array and re-links every existing {@link Node} against the new capacity —
     * the amortising cost that keeps average {@link #put} O(1). Nodes are moved, not recreated: their
     * cached {@code hash} is reused, only the bucket they hang off of changes.
     */
    private void resize() {
        Node<K, V>[] old = buckets;
        int newCapacity = old.length * 2;
        Node<K, V>[] newBuckets = newTable(newCapacity);
        for (Node<K, V> head : old) {
            Node<K, V> node = head;
            while (node != null) {
                Node<K, V> next = node.next;
                int idx = indexFor(node.hash, newCapacity);
                node.next = newBuckets[idx];
                newBuckets[idx] = node;
                node = next;
            }
        }
        buckets = newBuckets;
        threshold = (int) (newCapacity * LOAD_FACTOR);
    }
}
