package org.kata.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentLruCacheTest {

    // ------------------------------------------------------------------
    // Basic LRU behavioural tests (identical contract to LruCache)
    // ------------------------------------------------------------------

    @Test
    void get_returns_empty_for_missing_key() {
        var cache = new ConcurrentLruCache<String, Integer>(3);
        assertTrue(cache.get("absent").isEmpty());
    }

    @Test
    void put_and_get_roundtrip() {
        var cache = new ConcurrentLruCache<String, Integer>(3);
        cache.put("a", 1);
        assertEquals(1, cache.get("a").orElseThrow());
    }

    @Test
    void evicts_lru_entry_when_full() {
        var cache = new ConcurrentLruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        cache.put("d", 4); // evicts a

        assertTrue(cache.get("a").isEmpty());
        assertEquals(3, cache.size());
    }

    @Test
    void get_promotes_entry_so_it_survives_eviction() {
        var cache = new ConcurrentLruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        cache.get("a"); // promote a; b is now LRU

        cache.put("d", 4); // evicts b

        assertTrue(cache.get("b").isEmpty());
        assertEquals(1, cache.get("a").orElseThrow());
    }

    @Test
    void updating_existing_key_does_not_grow_size() {
        var cache = new ConcurrentLruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("a", 99);
        assertEquals(1, cache.size());
        assertEquals(99, cache.get("a").orElseThrow());
    }

    @Test
    void clear_empties_the_cache() {
        var cache = new ConcurrentLruCache<String, Integer>(5);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.get("a").isEmpty());
    }

    // ------------------------------------------------------------------
    // Concurrency tests
    // ------------------------------------------------------------------

    /**
     * Hammers put/get on overlapping keys from many virtual threads simultaneously.
     *
     * <p>Invariants checked:
     * <ul>
     *   <li>No exception is thrown by any thread — the lock prevents concurrent list corruption.</li>
     *   <li>{@code size()} never exceeds {@code capacity} at any observable point.</li>
     *   <li>All threads complete within the timeout — no deadlock or livelock.</li>
     * </ul>
     *
     * <p>The gate latch ensures all threads start work as simultaneously as the scheduler allows,
     * maximising the chance of exposing race conditions.
     */
    @Test
    void concurrent_puts_and_gets_never_corrupt_state() throws Exception {
        int capacity = 20;
        var cache = new ConcurrentLruCache<Integer, Integer>(capacity);

        int N = 300; // 300 virtual threads
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var exceptionSeen = new AtomicBoolean(false);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    // Each thread puts a key in [0, 30) — deliberate overlap to create
                    // contention on shared keys and trigger frequent evictions.
                    int key = i % 30;
                    cache.put(key, i);
                    cache.get(key);
                    // Spot-check size invariant from inside the thread.
                    int sz = cache.size();
                    if (sz > capacity) exceptionSeen.set(true);
                } catch (Exception e) {
                    exceptionSeen.set(true);
                } finally {
                    done.countDown();
                }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "threads did not finish — possible deadlock");
        }

        assertFalse(exceptionSeen.get(), "an exception or size violation was observed");
        assertTrue(cache.size() <= capacity, "final size exceeds capacity");
    }

    /**
     * Puts on disjoint key sets from two groups of threads, verifying the capacity invariant
     * holds when there is no key overlap (pure eviction pressure, no key sharing).
     */
    @Test
    void concurrent_puts_on_disjoint_keys_respect_capacity() throws Exception {
        int capacity = 10;
        var cache = new ConcurrentLruCache<Integer, Integer>(capacity);

        int N = 200;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            // Each thread gets a unique key in [0, N), all unique — pure eviction scenario.
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    cache.put(i, i * 2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "threads did not finish — possible deadlock");
        }

        assertTrue(cache.size() <= capacity,
                "cache size " + cache.size() + " exceeds capacity " + capacity);
    }

    /**
     * Concurrent gets on a pre-populated cache verify that promotions under concurrency
     * don't corrupt the list. If the linked list's pointers become inconsistent under
     * concurrent access, a subsequent get will often throw a NullPointerException.
     */
    @Test
    void concurrent_gets_do_not_corrupt_recency_list() throws Exception {
        int capacity = 5;
        var cache = new ConcurrentLruCache<Integer, Integer>(capacity);

        // Pre-populate to full capacity before starting threads.
        for (int i = 0; i < capacity; i++) {
            cache.put(i, i);
        }

        int N = 200;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var exceptionSeen = new AtomicBoolean(false);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    // All threads access keys [0, capacity), triggering concurrent promotions
                    // on the same keys — maximum contention on the recency list.
                    int key = i % capacity;
                    cache.get(key);
                } catch (Exception e) {
                    exceptionSeen.set(true);
                } finally {
                    done.countDown();
                }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertFalse(exceptionSeen.get(), "exception during concurrent gets — possible list corruption");
    }

    /**
     * Interleaved puts and gets from separate thread groups: one group is a pure writer
     * (inserting new keys), the other a pure reader (looking up existing keys). Verifies
     * that the writer group never causes a reader to observe an inconsistent (partially
     * promoted) state.
     *
     * <p>This test counts successful gets to confirm the cache has real data in it
     * throughout the test — it's not just silently returning empty.
     */
    @Test
    void concurrent_readers_and_writers_produce_no_exceptions() throws Exception {
        int capacity = 15;
        var cache = new ConcurrentLruCache<Integer, Integer>(capacity);

        // Pre-populate half capacity so readers have something to find.
        for (int i = 0; i < capacity / 2; i++) {
            cache.put(i, i);
        }

        int N = 200;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var exceptionSeen = new AtomicBoolean(false);
        var hits = new AtomicInteger(0);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    if (i % 2 == 0) {
                        // Writer: insert keys spread across a wider range to cause evictions.
                        cache.put(i % 50, i);
                    } else {
                        // Reader: look up keys in the pre-populated range.
                        cache.get(i % (capacity / 2)).ifPresent(v -> hits.incrementAndGet());
                    }
                } catch (Exception e) {
                    exceptionSeen.set(true);
                } finally {
                    done.countDown();
                }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertFalse(exceptionSeen.get(), "exception seen during concurrent read/write");
        // Readers saw at least some hits — the cache was not silently broken.
        assertTrue(hits.get() > 0, "expected some cache hits but got zero");
        assertTrue(cache.size() <= capacity);
    }
}
