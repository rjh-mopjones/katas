package org.kata.ringbuffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RingBufferTest {

    @Test
    void offer_then_poll_returns_elements_in_fifo_order() {
        RingBuffer<String> buffer = new RingBuffer<>(3);
        assertTrue(buffer.offer("a"));
        assertTrue(buffer.offer("b"));
        assertTrue(buffer.offer("c"));
        assertEquals("a", buffer.poll());
        assertEquals("b", buffer.poll());
        assertEquals("c", buffer.poll());
    }

    @Test
    void peek_returns_head_without_removing_it() {
        RingBuffer<Integer> buffer = new RingBuffer<>(2);
        buffer.offer(1);
        buffer.offer(2);
        assertEquals(1, buffer.peek());
        assertEquals(1, buffer.peek());
        assertEquals(2, buffer.size());
    }

    @Test
    void offer_when_full_returns_false_and_leaves_contents_unchanged() {
        RingBuffer<Integer> buffer = new RingBuffer<>(2);
        buffer.offer(1);
        buffer.offer(2);
        assertTrue(buffer.isFull());
        assertFalse(buffer.offer(3));
        assertEquals(2, buffer.size());
        assertEquals(1, buffer.poll());
        assertEquals(2, buffer.poll());
    }

    @Test
    void poll_and_peek_on_empty_buffer_return_null() {
        RingBuffer<String> buffer = new RingBuffer<>(4);
        assertNull(buffer.poll());
        assertNull(buffer.peek());
    }

    @Test
    void wrap_around_preserves_fifo_order_once_tail_crosses_the_array_end() {
        RingBuffer<Integer> buffer = new RingBuffer<>(3);
        buffer.offer(1);
        buffer.offer(2);
        buffer.offer(3);
        assertEquals(1, buffer.poll()); // head advances, freeing a slot near the array start
        assertEquals(2, buffer.poll());
        buffer.offer(4); // tail wraps past the end of the backing array
        buffer.offer(5);
        assertEquals(3, buffer.poll());
        assertEquals(4, buffer.poll());
        assertEquals(5, buffer.poll());
        assertTrue(buffer.isEmpty());
    }

    @Test
    void size_isEmpty_isFull_transition_correctly_across_offer_and_poll() {
        RingBuffer<Integer> buffer = new RingBuffer<>(2);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
        assertEquals(0, buffer.size());

        buffer.offer(1);
        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());
        assertEquals(1, buffer.size());

        buffer.offer(2);
        assertFalse(buffer.isEmpty());
        assertTrue(buffer.isFull());
        assertEquals(2, buffer.size());

        buffer.poll();
        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());
        assertEquals(1, buffer.size());

        buffer.poll();
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
        assertEquals(0, buffer.size());
    }

    @Test
    void capacity_of_one_alternates_between_empty_and_full() {
        RingBuffer<String> buffer = new RingBuffer<>(1);
        assertEquals(1, buffer.capacity());
        assertTrue(buffer.isEmpty());

        assertTrue(buffer.offer("only"));
        assertTrue(buffer.isFull());
        assertFalse(buffer.offer("second"));

        assertEquals("only", buffer.poll());
        assertTrue(buffer.isEmpty());

        assertTrue(buffer.offer("again"));
        assertEquals("again", buffer.peek());
    }

    @Test
    void capacity_reports_the_fixed_construction_time_size() {
        RingBuffer<Integer> buffer = new RingBuffer<>(5);
        assertEquals(5, buffer.capacity());
        buffer.offer(1);
        buffer.poll();
        assertEquals(5, buffer.capacity());
    }

    @Test
    void non_positive_capacity_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<Integer>(0));
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<Integer>(-3));
    }

    @Test
    void repeated_wrap_around_over_many_cycles_stays_correct() {
        RingBuffer<Integer> buffer = new RingBuffer<>(4);
        int nextValue = 0;
        for (int cycle = 0; cycle < 10; cycle++) {
            buffer.offer(nextValue++);
            buffer.offer(nextValue++);
            buffer.offer(nextValue++);
            assertEquals(nextValue - 3, buffer.poll());
            assertEquals(nextValue - 2, buffer.poll());
            assertEquals(nextValue - 1, buffer.poll());
            assertTrue(buffer.isEmpty());
        }
    }
}
