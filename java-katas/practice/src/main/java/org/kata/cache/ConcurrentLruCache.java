package org.kata.cache;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe LRU cache backed by an {@link LruCache} guarded by a single {@link ReentrantLock}.
 *
 * <h2>Why LRU's global ordering makes fine-grained locking hard</h2>
 *
 * <p>LRU requires that <em>every</em> cache hit promotes the accessed key to the most-recently-used
 * position. That promotion touches the global recency list — it is not a local operation scoped
 * to a single hash bucket. This means:
 * <ul>
 *   <li>Read operations ({@code get}) are not read-only: they mutate shared state (the doubly-
 *       linked list). A {@link java.util.concurrent.locks.ReadWriteLock} cannot help here —
 *       readers and writers all need the write lock.</li>
 *   <li>Lock-striping by key (as used in {@code ConcurrentHashMap}) doesn't work either:
 *       a promotion in stripe A is independent of a lookup in stripe B, but the shared tail
 *       pointer (eviction candidate) could be in any stripe. Coordinating eviction across
 *       stripes requires acquiring multiple stripe locks — right back to the complexity of
 *       lock ordering.</li>
 * </ul>
 *
 * <h2>The simple, correct approach chosen here</h2>
 *
 * <p>A single {@link ReentrantLock} serialises all access to the inner {@link LruCache}. This is
 * provably correct and easy to reason about. The trade-off is throughput: all threads queue on
 * one lock, so the cache becomes a bottleneck under heavy concurrent load. For many use cases
 * (read-mostly, low contention, or cache-aside patterns where misses are infrequent) this is
 * perfectly acceptable.
 *
 * <h2>Why {@link ReentrantLock} rather than {@code synchronized}</h2>
 *
 * <p>{@link ReentrantLock} is preferred here because it provides:
 * <ul>
 *   <li>{@code tryLock()} — useful for building non-blocking variants or adding metrics.</li>
 *   <li>Fairness option — prevents starvation if needed.</li>
 *   <li>Explicit {@code lock()}/{@code unlock()} with try/finally, which makes the critical
 *       section boundaries visible in the code (same style as {@code ConcurrentAccountService}).</li>
 * </ul>
 *
 * <h2>Production alternatives worth naming in an interview</h2>
 *
 * <ul>
 *   <li><b>Caffeine</b> — the state of the art for production JVM caches. Uses a segmented
 *       LRU approximation ({@code W-TinyLFU}) combined with a per-thread ring buffer of
 *       "access events" that are drained asynchronously onto the main data structure. Reads
 *       are near-lock-free: they write only to a thread-local buffer (cheap); eviction and
 *       ordering maintenance happen in a periodic drain step, amortised across many accesses.</li>
 *   <li><b>Striped approximate LRU</b> — split the key space across N segments, each with its
 *       own lock and LRU list. Eviction is approximate: evict from whichever segment is fullest.
 *       The global LRU property is lost but throughput scales with stripe count.</li>
 *   <li><b>Sampled LRU</b> (Redis's approach) — don't maintain a full recency list at all.
 *       On eviction, sample k random keys and evict the least recently used among the sample.
 *       O(k) eviction, zero overhead on gets, good enough hit rates in practice.</li>
 *   <li><b>ConcurrentLinkedHashMap</b> — Apache Cassandra's original concurrent LRU before
 *       Caffeine existed. Used a lock-striped map with a shared deque maintained via CAS.
 *       Higher throughput than a single lock, at significant implementation complexity.</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> value type
 */
public class ConcurrentLruCache<K, V> implements Cache<K, V> {

    /** The delegate single-threaded LRU cache. All access to it is guarded by {@code lock}. */
    private final LruCache<K, V> delegate;

    /**
     * Single lock protecting the entire delegate. Unfair by default: threads do not queue
     * strictly in arrival order, which gives higher throughput at the cost of potential (but
     * rare) starvation. Use {@code new ReentrantLock(true)} for a fair lock if starvation
     * is a concern in your deployment.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Creates a thread-safe LRU cache with the given capacity.
     *
     * @param capacity maximum number of entries; must be at least 1
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public ConcurrentLruCache(int capacity) {
        this.delegate = new LruCache<>(capacity);
    }

    /**
     * Thread-safe get. Acquires the lock, delegates to {@link LruCache#get}, releases.
     * Note: the lock is held during the promotion (list reorder), which is the mutation that
     * makes this method non-trivially different from a simple concurrent read.
     */
    @Override
    public Optional<V> get(K key) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Thread-safe put. Acquires the lock, delegates to {@link LruCache#put} (which may
     * evict one entry), releases. The entire evict-then-insert sequence is atomic from
     * the perspective of other threads.
     */
    @Override
    public void put(K key, V value) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Thread-safe size. Returns a point-in-time snapshot; by the time the caller uses the
     * value, it may have changed — callers should not make correctness-critical decisions
     * based on this without holding an external lock.
     */
    @Override
    public int size() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Thread-safe clear. Atomically removes all entries. Other threads blocked on {@code get}
     * or {@code put} will see an empty cache after this call completes.
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
