package org.kata.dynamicarray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicArrayTest {

    @Test
    void new_array_is_empty() {
        DynamicArray<String> array = new DynamicArray<>();
        assertTrue(array.isEmpty());
        assertEquals(0, array.size());
    }

    @Test
    void append_and_get_return_values_in_order() {
        DynamicArray<String> array = new DynamicArray<>();
        array.add("a");
        array.add("b");
        array.add("c");
        assertEquals(3, array.size());
        assertFalse(array.isEmpty());
        assertEquals("a", array.get(0));
        assertEquals("b", array.get(1));
        assertEquals("c", array.get(2));
    }

    @Test
    void growth_across_the_capacity_boundary_preserves_all_elements() {
        DynamicArray<Integer> array = new DynamicArray<>(2); // tiny initial capacity forces resizes
        for (int i = 0; i < 20; i++) {
            array.add(i);
        }
        assertEquals(20, array.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(i, array.get(i));
        }
    }

    @Test
    void insert_at_the_front_shifts_everything_right() {
        DynamicArray<String> array = arrayOf("b", "c");
        array.add(0, "a");
        assertEquals(3, array.size());
        assertEquals("a", array.get(0));
        assertEquals("b", array.get(1));
        assertEquals("c", array.get(2));
    }

    @Test
    void insert_in_the_middle_shifts_the_tail_right() {
        DynamicArray<String> array = arrayOf("a", "c", "d");
        array.add(1, "b");
        assertEquals(4, array.size());
        assertEquals("a", array.get(0));
        assertEquals("b", array.get(1));
        assertEquals("c", array.get(2));
        assertEquals("d", array.get(3));
    }

    @Test
    void insert_at_size_behaves_like_append() {
        DynamicArray<String> array = arrayOf("a", "b");
        array.add(2, "c");
        assertEquals(3, array.size());
        assertEquals("c", array.get(2));
    }

    @Test
    void remove_shifts_the_tail_left_and_returns_the_removed_element() {
        DynamicArray<String> array = arrayOf("a", "b", "c", "d");
        String removed = array.remove(1);
        assertEquals("b", removed);
        assertEquals(3, array.size());
        assertEquals("a", array.get(0));
        assertEquals("c", array.get(1));
        assertEquals("d", array.get(2));
    }

    @Test
    void remove_the_last_element_just_shrinks_size() {
        DynamicArray<String> array = arrayOf("a", "b");
        assertEquals("b", array.remove(1));
        assertEquals(1, array.size());
        assertEquals("a", array.get(0));
    }

    @Test
    void set_replaces_the_value_and_returns_the_old_one() {
        DynamicArray<String> array = arrayOf("a", "b", "c");
        String old = array.set(1, "z");
        assertEquals("b", old);
        assertEquals("z", array.get(1));
        assertEquals(3, array.size());
    }

    @Test
    void get_out_of_bounds_throws() {
        DynamicArray<String> array = arrayOf("a");
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(1));
    }

    @Test
    void set_out_of_bounds_throws() {
        DynamicArray<String> array = arrayOf("a");
        assertThrows(IndexOutOfBoundsException.class, () -> array.set(-1, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> array.set(1, "x"));
    }

    @Test
    void remove_out_of_bounds_throws() {
        DynamicArray<String> array = arrayOf("a");
        assertThrows(IndexOutOfBoundsException.class, () -> array.remove(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.remove(1));
    }

    @Test
    void indexed_add_out_of_bounds_throws() {
        DynamicArray<String> array = arrayOf("a");
        assertThrows(IndexOutOfBoundsException.class, () -> array.add(-1, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> array.add(2, "x"));
    }

    @Test
    void negative_initial_capacity_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new DynamicArray<String>(-1));
    }

    private static DynamicArray<String> arrayOf(String... values) {
        DynamicArray<String> array = new DynamicArray<>();
        for (String v : values) {
            array.add(v);
        }
        return array;
    }
}
