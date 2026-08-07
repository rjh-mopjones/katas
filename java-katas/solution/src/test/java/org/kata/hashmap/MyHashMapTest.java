package org.kata.hashmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MyHashMapTest {

    @Test
    void put_and_get_round_trip() {
        MyHashMap<String, Integer> map = new MyHashMap<>();
        map.put("a", 1);
        assertEquals(1, map.get("a"));
    }

    @Test
    void put_returns_previous_value_on_overwrite() {
        MyHashMap<String, Integer> map = new MyHashMap<>();
        assertNull(map.put("a", 1));
        assertEquals(1, map.put("a", 2));
        assertEquals(2, map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    void get_of_missing_key_returns_null() {
        MyHashMap<String, Integer> map = new MyHashMap<>();
        assertNull(map.get("missing"));
    }

    @Test
    void contains_key_reflects_presence() {
        MyHashMap<String, Integer> map = new MyHashMap<>();
        assertFalse(map.containsKey("a"));
        map.put("a", 1);
        assertTrue(map.containsKey("a"));
    }

    @Test
    void colliding_keys_are_both_stored_in_the_same_bucket() {
        MyHashMap<CollidingKey, String> map = new MyHashMap<>();
        CollidingKey k1 = new CollidingKey(1);
        CollidingKey k2 = new CollidingKey(2);

        map.put(k1, "one");
        map.put(k2, "two");

        assertEquals("one", map.get(k1));
        assertEquals("two", map.get(k2));
        assertEquals(2, map.size());
    }

    @Test
    void remove_from_middle_of_a_collision_chain_preserves_the_rest() {
        MyHashMap<CollidingKey, String> map = new MyHashMap<>();
        CollidingKey k1 = new CollidingKey(1);
        CollidingKey k2 = new CollidingKey(2);
        CollidingKey k3 = new CollidingKey(3);
        map.put(k1, "one");
        map.put(k2, "two");
        map.put(k3, "three");

        assertEquals("two", map.remove(k2));

        assertEquals("one", map.get(k1));
        assertNull(map.get(k2));
        assertFalse(map.containsKey(k2));
        assertEquals("three", map.get(k3));
        assertEquals(2, map.size());
    }

    @Test
    void remove_returns_null_for_a_missing_key() {
        MyHashMap<String, Integer> map = new MyHashMap<>();
        assertNull(map.remove("missing"));
    }

    @Test
    void null_key_is_supported() {
        MyHashMap<String, Integer> map = new MyHashMap<>();

        assertNull(map.put(null, 1));
        assertTrue(map.containsKey(null));
        assertEquals(1, map.get(null));

        assertEquals(1, map.put(null, 2));
        assertEquals(2, map.remove(null));

        assertNull(map.get(null));
        assertFalse(map.containsKey(null));
    }

    @Test
    void resize_preserves_all_entries() {
        MyHashMap<Integer, Integer> map = new MyHashMap<>();
        for (int i = 0; i < 1_000; i++) {
            map.put(i, i * i);
        }

        assertEquals(1_000, map.size());
        for (int i = 0; i < 1_000; i++) {
            assertEquals(i * i, map.get(i));
        }
    }

    @Test
    void resize_preserves_entries_starting_from_a_tiny_initial_capacity() {
        MyHashMap<Integer, Integer> map = new MyHashMap<>(1);
        for (int i = 0; i < 200; i++) {
            map.put(i, i);
        }

        assertEquals(200, map.size());
        for (int i = 0; i < 200; i++) {
            assertEquals(i, map.get(i));
        }
    }

    @Test
    void non_positive_initial_capacity_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new MyHashMap<Integer, Integer>(0));
        assertThrows(IllegalArgumentException.class, () -> new MyHashMap<Integer, Integer>(-1));
    }

    /** A key whose {@code hashCode} is constant so every instance collides into one bucket. */
    private static final class CollidingKey {
        private final int id;

        CollidingKey(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CollidingKey other && other.id == id;
        }
    }
}
