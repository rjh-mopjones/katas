package org.kata.ringbuffer;

/**
 * Fixed-capacity FIFO queue backed by a single array — the classic circular buffer.
 *
 * <p>The kind of thing behind {@link java.util.ArrayDeque}, a JVM's async logger ring, or an audio
 * driver's playback buffer: a producer offers elements, a consumer polls them, and the buffer never
 * grows past its construction-time {@code capacity}. Once full, {@link #offer} simply reports failure
 * rather than overwriting or resizing — callers decide whether to drop, block, or apply backpressure.
 *
 * <h2>Data structure</h2>
 * A single fixed-length {@code Object[] elements}, plus three cursors: {@code head} (index of the next
 * element to poll), {@code tail} (index of the next free slot to offer into), and {@code count} (how
 * many live elements there currently are). Both {@code head} and {@code tail} march forward modulo
 * {@code capacity} — when either would run off the end of the array it wraps back to {@code 0}. No
 * element is ever copied or the array reallocated after construction: {@code offer}/{@code poll} are
 * O(1) with zero per-element allocation.
 *
 * <h2>Full vs. empty disambiguation</h2>
 * With only {@code head} and {@code tail}, {@code head == tail} is ambiguous — it means both "empty"
 * and "full" (tail has wrapped all the way around to meet head). The classic fixes are: burn one array
 * slot so the buffer can only ever hold {@code capacity - 1} elements, or track a separate counter. This
 * implementation keeps the third field, {@code count}, as the single source of truth for
 * {@link #isEmpty()} / {@link #isFull()} / {@link #size()} — every real slot in {@code elements} stays
 * usable and the cursor arithmetic never needs a special case for the wrap point.
 *
 * <h2>Trade-offs</h2>
 * A count field costs 4 (or 8, with padding) bytes over the slot-burning trick, in exchange for a
 * simpler, branch-free mental model — every method reasons about "how many are there" directly instead
 * of back-deriving it from where the cursors happen to sit. Unlike a resizable queue there is no
 * amortised-growth cost to reason about, but also no growth: capacity is fixed for the buffer's
 * lifetime, which is exactly the point for a bounded, latency-sensitive consumer.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>{@code Iterable<E>} in logical order</b> — a custom {@link java.util.Iterator} that walks
 *       {@code count} elements starting at {@code head}, wrapping through the array, without exposing
 *       the raw backing order.</li>
 *   <li><b>Double-ended</b> ({@code offerFirst}/{@code offerLast}, {@code pollFirst}/{@code pollLast}) —
 *       {@code head} can be decremented (mod {@code capacity}) as well as incremented, turning this into
 *       an array-backed deque.</li>
 *   <li><b>Lock-free SPSC cousin</b> — a single producer and single consumer thread can share this same
 *       layout without a lock by making {@code head}/{@code tail} {@code volatile} (or
 *       {@link java.util.concurrent.atomic.AtomicInteger}) and re-deriving full/empty from the cursors
 *       instead of a shared {@code count}, since a plain int written by one thread and read by another
 *       needs a happens-before edge; see {@code [[lockfree]]}.</li>
 * </ul>
 */
public final class RingBuffer<E> {

    // Object[] rather than E[]: generic array creation (`new E[capacity]`) is illegal because the
    // component type is erased at runtime — the JVM cannot verify an array of E. Casting on read
    // (`(E) elements[i]`) is the standard, unchecked-but-safe workaround: this class alone controls
    // what goes into the array, so the cast can never actually fail.
    private final Object[] elements;
    private int head;
    private int tail;
    private int count;

    public RingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.elements = new Object[capacity];
    }

    /** Append {@code e} at the tail. @return {@code false} (and leaves the buffer unchanged) if full. */
    public boolean offer(E e) {
        if (isFull()) {
            return false;
        }
        elements[tail] = e;
        tail = (tail + 1) % elements.length;
        count++;
        return true;
    }

    /** Remove and return the head element, or {@code null} if empty. */
    @SuppressWarnings("unchecked")
    public E poll() {
        if (isEmpty()) {
            return null;
        }
        E value = (E) elements[head];
        elements[head] = null; // drop the reference so a polled object isn't pinned by a stale slot
        head = (head + 1) % elements.length;
        count--;
        return value;
    }

    /** Return (without removing) the head element, or {@code null} if empty. */
    @SuppressWarnings("unchecked")
    public E peek() {
        if (isEmpty()) {
            return null;
        }
        return (E) elements[head];
    }

    /** Number of elements currently held. */
    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public boolean isFull() {
        return count == elements.length;
    }

    /** Fixed capacity supplied at construction; never changes. */
    public int capacity() {
        return elements.length;
    }
}
