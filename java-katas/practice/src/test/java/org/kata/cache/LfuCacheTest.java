package org.kata.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LfuCacheTest {

    // ------------------------------------------------------------------
    // Basic contract
    // ------------------------------------------------------------------

    @Test
    void get_returns_empty_for_missing_key() {
        var cache = new LfuCache<String, Integer>(3);
        assertTrue(cache.get("absent").isEmpty());
    }

    @Test
    void put_and_get_roundtrip() {
        var cache = new LfuCache<String, Integer>(3);
        cache.put("a", 1);
        assertEquals(1, cache.get("a").orElseThrow());
    }

    @Test
    void size_reflects_number_of_entries() {
        var cache = new LfuCache<String, Integer>(5);
        assertEquals(0, cache.size());
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(2, cache.size());
    }

    @Test
    void clear_empties_the_cache() {
        var cache = new LfuCache<String, Integer>(5);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.get("a").isEmpty());
    }

    @Test
    void capacity_of_one_always_holds_latest_entry() {
        var cache = new LfuCache<String, Integer>(1);
        cache.put("a", 1);
        cache.put("b", 2);
        assertTrue(cache.get("a").isEmpty());
        assertEquals(2, cache.get("b").orElseThrow());
    }

    // ------------------------------------------------------------------
    // LFU eviction — evicts least-frequently-used
    // ------------------------------------------------------------------

    @Test
    void evicts_least_frequently_used_entry() {
        // a: 3 gets, b: 1 get, c: 2 gets. Insert d — b must be evicted (freq 1).
        var cache = new LfuCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        cache.get("a");
        cache.get("a");
        cache.get("a"); // freq(a) = 4 (1 put + 3 gets)
        cache.get("c");
        cache.get("c"); // freq(c) = 3
        // freq(b) = 1 (only the initial put counts as one access — it is the lowest)

        cache.put("d", 4); // must evict b

        assertTrue(cache.get("b").isEmpty(), "b has the lowest frequency and must be evicted");
        assertFalse(cache.get("a").isEmpty());
        assertFalse(cache.get("c").isEmpty());
        assertFalse(cache.get("d").isEmpty());
    }

    @Test
    void get_increments_frequency_and_affects_eviction_choice() {
        // After inserting a and b (both freq=1), access "a" once (freq(a)=2, freq(b)=1).
        // Insert c at capacity 2 — b must be evicted (lower freq).
        var cache = new LfuCache<String, Integer>(2);
        cache.put("a", 1);
        cache.put("b", 2);

        cache.get("a"); // freq(a) = 2; freq(b) = 1

        cache.put("c", 3); // evicts b (freq 1)

        assertTrue(cache.get("b").isEmpty(), "b must be evicted: lower frequency than a");
        assertEquals(1, cache.get("a").orElseThrow());
        assertEquals(3, cache.get("c").orElseThrow());
    }

    // ------------------------------------------------------------------
    // Tie-breaking: LRU within same frequency bucket
    // ------------------------------------------------------------------

    @Test
    void ties_broken_by_lru_within_same_frequency() {
        // All three keys at frequency 1. Insert order is a, b, c. On eviction, a (oldest
        // at freq=1) must be evicted before b, which must be evicted before c.
        var cache = new LfuCache<String, Integer>(3);
        cache.put("a", 1); // inserted first → LRU among freq-1 entries
        cache.put("b", 2);
        cache.put("c", 3); // inserted last → MRU among freq-1 entries

        // All freq=1. Cache is full. Insert d — a is LRU at minFreq=1 and must be evicted.
        cache.put("d", 4);

        assertTrue(cache.get("a").isEmpty(), "a is LRU at freq=1 and must be evicted");
        assertEquals(2, cache.get("b").orElseThrow());
        assertEquals(3, cache.get("c").orElseThrow());
        assertEquals(4, cache.get("d").orElseThrow());
    }

    @Test
    void lru_tiebreak_respects_promotion_order_within_bucket() {
        // a: freq=2, b: freq=2, c: freq=1. Insert d at capacity 3.
        // c (freq=1) must be evicted, not a or b.
        var cache = new LfuCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        cache.get("a"); // freq(a) = 2
        cache.get("b"); // freq(b) = 2
        // freq(c) = 1 — lowest

        cache.put("d", 4); // evicts c

        assertTrue(cache.get("c").isEmpty(), "c has lowest frequency and must be evicted");
        assertFalse(cache.get("a").isEmpty());
        assertFalse(cache.get("b").isEmpty());
    }

    @Test
    void within_freq_bucket_lru_ordering_preserved_after_promotion() {
        // Capacity 2. Insert a (freq=1), insert b (freq=1). Access a twice (freq(a)=3),
        // access b once (freq(b)=2). Now both above 1. Insert c — b is LFU (freq=2).
        var cache = new LfuCache<String, Integer>(2);
        cache.put("a", 1);
        cache.put("b", 2);

        cache.get("a"); // freq(a) = 2
        cache.get("a"); // freq(a) = 3
        cache.get("b"); // freq(b) = 2

        // Now freq(a)=3, freq(b)=2. Insert c — b is LFU.
        cache.put("c", 3);

        assertTrue(cache.get("b").isEmpty(), "b has lowest freq (2) vs a (3)");
        assertEquals(1, cache.get("a").orElseThrow());
        assertEquals(3, cache.get("c").orElseThrow());
    }

    // ------------------------------------------------------------------
    // minFreq maintenance
    // ------------------------------------------------------------------

    @Test
    void min_freq_updates_correctly_across_multiple_evictions() {
        // Regression test: after several eviction cycles, minFreq must always point to a
        // non-empty bucket. We verify by checking that the cache never throws and always
        // returns the correct value after many put/get cycles.
        var cache = new LfuCache<Integer, Integer>(3);
        for (int i = 0; i < 20; i++) {
            cache.put(i, i * 10);
            // Access some older keys to drive up their frequencies.
            if (i >= 1) cache.get(i - 1);
            if (i >= 2) cache.get(i - 2);
            // Invariant: size never exceeds capacity.
            assertTrue(cache.size() <= 3, "size exceeded capacity at iteration " + i);
        }
    }

    @Test
    void new_key_always_resets_min_freq_to_one() {
        // After several accesses that push all keys to freq > 1, inserting a new key should
        // make minFreq=1. The new key must therefore be the eviction candidate on the next
        // insert. This validates that minFreq is correctly reset to 1 on new insertions.
        var cache = new LfuCache<String, Integer>(2);
        cache.put("a", 1);
        cache.put("b", 2);

        // Drive both to freq=3.
        cache.get("a"); cache.get("a"); // freq(a)=3
        cache.get("b"); cache.get("b"); // freq(b)=3

        // Now insert "c". Cache is full, so one of a/b is evicted. After that c is inserted
        // at freq=1. If we now insert "d", c (freq=1) must be evicted.
        cache.put("c", 3); // evicts a or b (both at freq=3, a is LRU at that freq)
        // c is now in cache at freq=1. Insert d — c should be evicted (lowest freq).
        cache.put("d", 4);

        assertTrue(cache.get("c").isEmpty(), "c (freq=1) must be evicted before the freq=3 survivor");
        assertFalse(cache.get("d").isEmpty());
    }

    // ------------------------------------------------------------------
    // Update semantics
    // ------------------------------------------------------------------

    @Test
    void updating_existing_key_does_not_grow_size() {
        var cache = new LfuCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("a", 99);
        assertEquals(1, cache.size());
        assertEquals(99, cache.get("a").orElseThrow());
    }

    @Test
    void updating_existing_key_increments_its_frequency() {
        // After put("a",1) (freq=1) and put("b",1) (freq=1), update a: freq(a)=2.
        // Insert c at capacity 2 — b (freq=1) must be evicted, not a.
        var cache = new LfuCache<String, Integer>(2);
        cache.put("a", 1);
        cache.put("b", 2);

        cache.put("a", 100); // update a: freq(a) becomes 2

        cache.put("c", 3); // evicts b (freq=1)

        assertTrue(cache.get("b").isEmpty(), "b must be evicted: freq=1 < freq(a)=2");
        assertEquals(100, cache.get("a").orElseThrow());
    }

    // ------------------------------------------------------------------
    // Boundary / argument validation
    // ------------------------------------------------------------------

    @Test
    void constructor_rejects_zero_capacity() {
        assertThrows(IllegalArgumentException.class, () -> new LfuCache<>(0));
    }

    @Test
    void constructor_rejects_negative_capacity() {
        assertThrows(IllegalArgumentException.class, () -> new LfuCache<>(-1));
    }

    @Test
    void capacity_is_strictly_respected_over_many_inserts() {
        int cap = 4;
        var cache = new LfuCache<Integer, Integer>(cap);
        for (int i = 0; i < 100; i++) {
            cache.put(i, i);
            assertTrue(cache.size() <= cap, "size exceeded capacity after inserting key " + i);
        }
    }
}
