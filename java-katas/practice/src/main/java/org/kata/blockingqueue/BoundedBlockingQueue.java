package org.kata.blockingqueue;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Generic bounded blocking queue backed by a circular array.
 *
 * <p>This is a teaching re-implementation of {@link java.util.concurrent.ArrayBlockingQueue}
 * — one of the most commonly asked data-structure problems in Java concurrency interviews
 * because it requires mastery of {@link ReentrantLock}, {@link Condition}, and the subtle
 * differences between {@code signal()} and {@code signalAll()}.
 *
 * <h2>Data structure: circular array</h2>
 * A fixed-length array with two integer cursors — {@code head} (next read position) and
 * {@code tail} (next write position) — both advancing modulo {@code capacity}. When
 * {@code size == capacity} the queue is full; when {@code size == 0} it is empty. The circular
 * array avoids the memory allocation overhead of a linked list and provides O(1) enqueue and
 * dequeue with excellent cache locality.
 *
 * <h2>One lock, two conditions</h2>
 * All state mutations are guarded by a single {@link ReentrantLock}. Two separate
 * {@link Condition}s are derived from it:
 * <ul>
 *   <li>{@code notFull} — producers wait here when the queue is full; consumers signal it
 *       after a successful take.</li>
 *   <li>{@code notEmpty} — consumers wait here when the queue is empty; producers signal it
 *       after a successful put.</li>
 * </ul>
 *
 * <h3>Why two conditions instead of one?</h3>
 * With a single condition ({@code lock.newCondition()} used for everything), both producers
 * and consumers wait on the same queue. After a producer puts an item it must call
 * {@code signalAll()} rather than {@code signal()} because {@code signal()} might wake another
 * producer (which is stuck waiting for space, not waiting for data). That woken producer
 * immediately re-blocks after checking the full condition — wasted work. With two separate
 * conditions, a producer calls {@code notEmpty.signal()} to wake exactly one consumer, and a
 * consumer calls {@code notFull.signal()} to wake exactly one producer. No spurious wakeups of
 * the wrong party; under high contention this halves unnecessary context switches.
 *
 * <h3>Why {@code while} loops, not {@code if}?</h3>
 * {@link Condition#await()} can return for two reasons:
 * <ol>
 *   <li>Another thread called {@code signal()} or {@code signalAll()} — the expected wake.</li>
 *   <li>A <em>spurious wakeup</em> — a wakeup with no corresponding signal, permitted by
 *       POSIX pthreads and therefore by the JVM specification. If the condition is checked
 *       only with {@code if}, a spurious wakeup skips the check and the thread proceeds with
 *       invalid state. A {@code while} loop re-checks the predicate after every wakeup,
 *       handling both spurious wakeups and the case where another thread consumed the newly
 *       available slot/item before this thread was scheduled.</li>
 * </ol>
 *
 * <h2>Production alternative</h2>
 * Use {@link java.util.concurrent.ArrayBlockingQueue} in production. It is this class but with
 * battle-tested corner-case handling, optional fairness, and bulk-copy optimisations. This
 * re-implementation exists solely to understand and explain the mechanisms underneath.
 *
 * @param <E> the type of elements held in this queue; must not be null.
 */
public class BoundedBlockingQueue<E> {

    // ---- Storage ----

    /**
     * The backing array. Sized to {@code capacity}; elements are stored at positions
     * {@code [head, head+1, …, head+size-1] mod capacity}.
     */
    private final Object[] buffer;

    private final int capacity;

    /** Index of the next element to be taken. Advances after each take. */
    private int head = 0;

    /** Index of the next slot to be written. Advances after each put. */
    private int tail = 0;

    /** Current number of elements in the queue. */
    private int size = 0;

    // ---- Concurrency primitives ----

    private final ReentrantLock lock = new ReentrantLock();

    /** Producers await this when the queue is full; consumers signal it after a take. */
    private final Condition notFull = lock.newCondition();

    /** Consumers await this when the queue is empty; producers signal it after a put. */
    private final Condition notEmpty = lock.newCondition();

    // ---- Constructor ----

    /**
     * @param capacity maximum number of elements the queue can hold; must be ≥ 1.
     */
    public BoundedBlockingQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    // ---- Public API ----

    /**
     * Insert {@code element} at the tail of the queue, blocking until space is available.
     *
     * @param element the element to add; must not be null (null would be ambiguous with an
     *                empty-queue sentinel in some designs and is disallowed for clarity).
     * @throws InterruptedException if the calling thread is interrupted while waiting.
     */
    public void put(E element) throws InterruptedException {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Remove and return the head element, blocking until one is available.
     *
     * @return the head element.
     * @throws InterruptedException if the calling thread is interrupted while waiting.
     */
    @SuppressWarnings("unchecked")
    public E take() throws InterruptedException {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Returns the current number of elements in the queue.
     *
     * <p>This is a point-in-time snapshot. The value may change immediately after the method
     * returns. Use it for monitoring, not for conditional logic (the queue's own {@code put}
     * and {@code take} already handle the blocking internally).
     */
    public int size() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Returns the maximum number of elements this queue can hold.
     */
    public int capacity() {
        return capacity;
    }
}
