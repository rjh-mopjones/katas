package org.kata.dynamicarray;

import java.util.Arrays;

/**
 * Index-based growable array — the structure behind {@link java.util.ArrayList}.
 *
 * <p>Backed by a single {@code Object[]} that grows geometrically as elements are appended, giving
 * O(1) amortised {@link #add(Object)} and O(1) random access via {@link #get(int)}. Indexed
 * {@link #add(int, Object)} / {@link #remove(int)} stay O(n) because they must shift the tail to
 * keep the array dense and contiguous — the fundamental trade-off against a linked structure, which
 * gets O(1) insert/remove at a known node but loses O(1) indexing.
 *
 * <h2>Growth strategy</h2>
 * On overflow the backing array grows to {@code oldCapacity + (oldCapacity >> 1)} — roughly 1.5x —
 * copied with {@link System#arraycopy}. Doubling (2x) is the more common textbook choice; 1.5x is
 * what the JDK's {@code ArrayList} actually uses, trading a few more resizes for less wasted memory
 * per resize. Either constant keeps the <em>amortised</em> cost of N appends at O(N) total, i.e. O(1)
 * per append on average: each element is copied only O(log N) times across all resizes, and the
 * geometric series of copy costs (1 + r + r^2 + ...) sums to a constant multiple of N.
 *
 * <h2>Generic array creation</h2>
 * Java erases generic type parameters at runtime, so {@code new E[capacity]} does not compile. The
 * backing store is declared {@code Object[]} and every read casts back to {@code E} with
 * {@code @SuppressWarnings("unchecked")} — safe because {@link #add(Object)}, {@link #add(int,
 * Object)} and {@link #set(int, Object)} are the only writers and all three are typed {@code E} at
 * compile time, so nothing but an {@code E} (or {@code null}) is ever stored in a live slot.
 *
 * <h2>Shifting on indexed insert/remove</h2>
 * {@link #add(int, Object)} opens a one-slot gap by copying {@code [index, size)} one position right
 * <em>before</em> writing the new element; {@link #remove(int)} closes the gap by copying
 * {@code [index + 1, size)} one position left, then nulls the now-unused last slot so the removed
 * reference isn't pinned by a lingering array slot (a real, if minor, memory-leak trap).
 *
 * <h2>Bounds checking</h2>
 * Two different validity ranges are easy to conflate: {@link #get(int)}, {@link #set(int, Object)}
 * and {@link #remove(int)} require {@code 0 <= index < size} (the index must name an existing
 * element), while {@link #add(int, Object)} additionally allows {@code index == size} (inserting at
 * the end is a valid append). Both throw {@link IndexOutOfBoundsException} on violation.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Fail-fast {@link java.util.Iterator}</b> — track a {@code modCount}, bump it on every
 *       structural change, and check it in {@code next()}/{@code hasNext()} to detect concurrent
 *       modification during iteration, like {@code ArrayList}'s.</li>
 *   <li><b>{@code ensureCapacity(int)}</b> — expose the growth hook publicly so a caller who knows
 *       the eventual size up front can pre-size once and avoid intermediate resizes.</li>
 *   <li><b>Shrinking</b> — halve the backing array when {@code size} falls far enough below
 *       {@code capacity} (e.g. below a quarter) to bound memory after a large removal burst, at the
 *       cost of occasional extra copies (the same amortised argument as growth, run in reverse).</li>
 * </ul>
 */
public final class DynamicArray<E> {

    private static final int DEFAULT_CAPACITY = 10;

    private Object[] elements;
    private int size;

    public DynamicArray() {
        this(DEFAULT_CAPACITY);
    }

    public DynamicArray(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity must be >= 0: " + initialCapacity);
        }
        this.elements = new Object[initialCapacity];
    }

    /** Append {@code e} to the end, growing the backing array first if it is full. */
    public void add(E e) {
        ensureCapacity(size + 1);
        elements[size++] = e;
    }

    /**
     * Insert {@code e} at {@code index}, shifting the elements at {@code [index, size)} one position
     * right.
     *
     * @throws IndexOutOfBoundsException if {@code index < 0 || index > size}
     */
    public void add(int index, E e) {
        checkIndexForInsert(index);
        ensureCapacity(size + 1);
        System.arraycopy(elements, index, elements, index + 1, size - index);
        elements[index] = e;
        size++;
    }

    /**
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size}
     */
    @SuppressWarnings("unchecked") // only add/set ever write a slot, always with an E
    public E get(int index) {
        checkIndexForAccess(index);
        return (E) elements[index];
    }

    /**
     * Replace the element at {@code index} and return the value it held.
     *
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size}
     */
    @SuppressWarnings("unchecked")
    public E set(int index, E e) {
        checkIndexForAccess(index);
        E old = (E) elements[index];
        elements[index] = e;
        return old;
    }

    /**
     * Remove and return the element at {@code index}, shifting the elements at
     * {@code [index + 1, size)} one position left.
     *
     * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size}
     */
    @SuppressWarnings("unchecked")
    public E remove(int index) {
        checkIndexForAccess(index);
        E removed = (E) elements[index];
        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved);
        }
        size--;
        elements[size] = null; // drop the reference so it isn't pinned past the logical end
        return removed;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Grow the backing array by ~1.5x if it cannot hold {@code minCapacity} elements. */
    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= elements.length) {
            return;
        }
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity < minCapacity) {
            newCapacity = minCapacity; // covers capacity 0/1 where 1.5x alone can't catch up
        }
        elements = Arrays.copyOf(elements, newCapacity);
    }

    /** {@code get}/{@code set}/{@code remove}: index must name an existing element. */
    private void checkIndexForAccess(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    /** {@code add(index, e)}: index may additionally equal {@code size} (append). */
    private void checkIndexForInsert(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }
}
