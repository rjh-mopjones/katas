package org.kata.heap;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BinaryHeapTest {

    @Test
    void empty_heap_peek_and_poll_return_null() {
        BinaryHeap<Integer> heap = new BinaryHeap<>();
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        assertNull(heap.peek());
        assertNull(heap.poll());
    }

    @Test
    void default_ctor_polls_in_ascending_order_min_heap() {
        BinaryHeap<Integer> heap = new BinaryHeap<>();
        heap.add(5);
        heap.add(1);
        heap.add(4);
        heap.add(2);
        heap.add(3);

        assertEquals(5, heap.size());
        assertEquals(1, heap.poll());
        assertEquals(2, heap.poll());
        assertEquals(3, heap.poll());
        assertEquals(4, heap.poll());
        assertEquals(5, heap.poll());
        assertTrue(heap.isEmpty());
    }

    @Test
    void peek_returns_root_without_removing_it() {
        BinaryHeap<Integer> heap = new BinaryHeap<>();
        heap.add(3);
        heap.add(1);
        heap.add(2);

        assertEquals(1, heap.peek());
        assertEquals(3, heap.size());
        assertEquals(1, heap.peek());
    }

    @Test
    void comparator_reverse_order_makes_a_max_heap() {
        BinaryHeap<Integer> heap = new BinaryHeap<>(Comparator.reverseOrder());
        heap.add(5);
        heap.add(1);
        heap.add(4);
        heap.add(2);
        heap.add(3);

        assertEquals(5, heap.poll());
        assertEquals(4, heap.poll());
        assertEquals(3, heap.poll());
        assertEquals(2, heap.poll());
        assertEquals(1, heap.poll());
    }

    @Test
    void duplicate_elements_are_all_retained_and_ordered() {
        BinaryHeap<Integer> heap = new BinaryHeap<>();
        heap.add(2);
        heap.add(2);
        heap.add(1);
        heap.add(1);
        heap.add(2);

        assertEquals(5, heap.size());
        assertEquals(1, heap.poll());
        assertEquals(1, heap.poll());
        assertEquals(2, heap.poll());
        assertEquals(2, heap.poll());
        assertEquals(2, heap.poll());
    }

    @Test
    void poll_to_empty_then_poll_again_returns_null() {
        BinaryHeap<Integer> heap = new BinaryHeap<>();
        heap.add(1);
        heap.add(2);

        assertEquals(1, heap.poll());
        assertEquals(2, heap.poll());
        assertTrue(heap.isEmpty());
        assertNull(heap.poll());
        assertNull(heap.peek());
    }

    @Test
    void heapify_constructor_builds_from_a_collection_and_drains_sorted() {
        List<Integer> items = List.of(9, 3, 7, 1, 8, 2, 6, 4, 5, 0);
        BinaryHeap<Integer> heap = new BinaryHeap<>(items, Comparator.naturalOrder());

        assertEquals(items.size(), heap.size());
        for (int expected = 0; expected <= 9; expected++) {
            assertEquals(expected, heap.poll());
        }
        assertTrue(heap.isEmpty());
    }

    @Test
    void heapify_constructor_with_empty_collection_is_an_empty_heap() {
        BinaryHeap<Integer> heap = new BinaryHeap<>(List.<Integer>of(), Comparator.naturalOrder());
        assertTrue(heap.isEmpty());
        assertNull(heap.poll());
    }

    @Test
    void handles_many_elements_across_capacity_growth() {
        BinaryHeap<Integer> heap = new BinaryHeap<>();
        int n = 1_000;
        for (int i = n - 1; i >= 0; i--) {
            heap.add(i);
        }
        assertEquals(n, heap.size());
        for (int expected = 0; expected < n; expected++) {
            assertEquals(expected, heap.poll());
        }
    }
}
