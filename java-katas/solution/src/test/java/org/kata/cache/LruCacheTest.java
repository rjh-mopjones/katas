package org.kata.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LruCacheTest {

    // ------------------------------------------------------------------
    // Basic contract
    // ------------------------------------------------------------------

    @Test
    void get_returns_empty_for_missing_key() {
        var cache = new LruCache<String, Integer>(3);
        assertTrue(cache.get("absent").isEmpty());
    }

    @Test
    void put_and_get_roundtrip() {
        var cache = new LruCache<String, Integer>(3);
        cache.put("a", 1);
        assertEquals(1, cache.get("a").orElseThrow());
    }

    @Test
    void size_reflects_number_of_entries() {
        var cache = new LruCache<String, Integer>(5);
        assertEquals(0, cache.size());
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(2, cache.size());
    }

    @Test
    void clear_empties_the_cache() {
        var cache = new LruCache<String, Integer>(5);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.get("a").isEmpty());
    }

    @Test
    void capacity_of_one_always_holds_latest_entry() {
        var cache = new LruCache<String, Integer>(1);
        cache.put("a", 1);
        cache.put("b", 2);
        // "a" must have been evicted to make room for "b"
        assertTrue(cache.get("a").isEmpty());
        assertEquals(2, cache.get("b").orElseThrow());
    }

    // ------------------------------------------------------------------
    // Eviction order
    // ------------------------------------------------------------------

    @Test
    void evicts_lru_entry_when_full() {
        // Capacity 3; insert a, b, c (full); insert d — "a" is LRU and must be evicted.
        var cache = new LruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        cache.put("d", 4);

        assertTrue(cache.get("a").isEmpty(),  "a was LRU and should be evicted");
        assertEquals(2, cache.get("b").orElseThrow());
        assertEquals(3, cache.get("c").orElseThrow());
        assertEquals(4, cache.get("d").orElseThrow());
        assertEquals(3, cache.size());
    }

    @Test
    void get_promotes_entry_so_it_is_not_evicted_next() {
        // Insert a, b, c. Access "a" (promoting it past b). Then insert "d".
        // Without promotion, "a" would be LRU and evicted. With promotion, "b" is LRU.
        var cache = new LruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        // Promote "a" — access order is now: c (MRU), a, b (LRU)
        cache.get("a");

        cache.put("d", 4); // should evict "b", not "a"

        assertTrue(cache.get("b").isEmpty(), "b was LRU after promotion of a");
        assertEquals(1, cache.get("a").orElseThrow(), "a was promoted and must survive");
        assertEquals(4, cache.get("d").orElseThrow());
    }

    @Test
    void eviction_picks_true_lru_after_interleaved_accesses() {
        // Interleave accesses to verify ordering is maintained precisely.
        // Capacity 3: insert a, b, c. Access b, then a. Order is a (MRU), b, c (LRU).
        // Insert d — c must be evicted.
        var cache = new LruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        cache.get("b"); // order: b (MRU), c, a (LRU) ... wait: inserts are a, b, c so order before access: c (MRU), b, a (LRU)
        // After get("b"): b (MRU), c, a (LRU)
        cache.get("a"); // After get("a"): a (MRU), b, c (LRU)

        cache.put("d", 4); // evicts c (LRU)

        assertTrue(cache.get("c").isEmpty(), "c is LRU and must be evicted");
        assertEquals(1, cache.get("a").orElseThrow());
        assertEquals(2, cache.get("b").orElseThrow());
        assertEquals(4, cache.get("d").orElseThrow());
    }

    // ------------------------------------------------------------------
    // Update semantics
    // ------------------------------------------------------------------

    @Test
    void updating_existing_key_does_not_grow_size() {
        var cache = new LruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("a", 99);
        assertEquals(1, cache.size());
        assertEquals(99, cache.get("a").orElseThrow());
    }

    @Test
    void updating_existing_key_promotes_it_to_mru() {
        // Insert a, b, c. Update "a" (promoting it). Insert d — b should be evicted (now LRU).
        var cache = new LruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        // Update "a": order becomes a (MRU), c, b (LRU)
        cache.put("a", 100);

        cache.put("d", 4); // evicts b

        assertTrue(cache.get("b").isEmpty(), "b is LRU after a was promoted via update");
        assertEquals(100, cache.get("a").orElseThrow());
        assertEquals(3, cache.get("c").orElseThrow());
    }

    // ------------------------------------------------------------------
    // Boundary / argument validation
    // ------------------------------------------------------------------

    @Test
    void constructor_rejects_zero_capacity() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<>(0));
    }

    @Test
    void constructor_rejects_negative_capacity() {
        assertThrows(IllegalArgumentException.class, () -> new LruCache<>(-1));
    }

    @Test
    void capacity_is_strictly_respected_over_many_inserts() {
        int cap = 5;
        var cache = new LruCache<Integer, Integer>(cap);
        for (int i = 0; i < 100; i++) {
            cache.put(i, i);
            assertTrue(cache.size() <= cap, "size exceeded capacity after inserting key " + i);
        }
    }
}
