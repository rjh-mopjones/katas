package org.kata.heap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

/**
 * Array-backed binary heap — the structure behind {@link java.util.PriorityQueue}.
 *
 * <p>A heap is a <b>complete binary tree</b> (every level full except possibly the last, which
 * fills left-to-right) stored implicitly in an array: for a node at index {@code i}, its children
 * live at {@code 2i+1} and {@code 2i+2}, and its parent at {@code (i-1)/2}. Completeness is what
 * lets the tree be an array with no pointers — the shape is fully determined by {@code size}, so
 * there is nothing to rebalance and no node objects to allocate.
 *
 * <h2>Heap-order invariant</h2>
 * Every node compares "not after" its children under the configured {@link Comparator}: for a
 * min-heap, {@code cmp.compare(parent, child) <= 0} at every edge. That invariant only constrains
 * parent-to-child, not sibling-to-sibling or across subtrees — so the heap is <em>not</em> sorted,
 * only the root (index 0) is guaranteed to be the extreme element. That weaker invariant is exactly
 * why {@link #add} and {@link #poll} are O(log n) instead of the O(n) or O(log n)-with-shifting a
 * fully sorted structure would need.
 *
 * <h2>Sift operations</h2>
 * <ul>
 *   <li>{@code siftUp} — after appending a new element at the last slot, swap it with its parent
 *       while it compares before the parent. Bubbles the new element up to its resting place;
 *       touches only the path from the leaf to (at most) the root, so O(log n).</li>
 *   <li>{@code siftDown} — after {@link #poll} moves the last element into the vacated root, swap it
 *       with its "better" child while a child compares before it. Bubbles the misplaced root down to
 *       its resting place; also O(log n).</li>
 * </ul>
 * Both operations only ever swap along a single root-to-leaf path, which is the whole complexity
 * argument: tree height is {@code floor(log2(n))} because the tree is complete.
 *
 * <h2>Backing store &amp; erasure</h2>
 * Elements are held in an {@code Object[]} rather than {@code E[]} because Java forbids generic
 * array creation ({@code new E[cap]}) — the component type is erased at runtime, so the JVM cannot
 * build an array of it. Every read casts back to {@code E}; the cast is safe because {@link #add}
 * is the only writer and it is fully generic. An {@link ArrayList}{@code <E>} would sidestep the
 * cast but costs an extra layer of boxing/indirection for what is otherwise a flat array; the raw
 * array matches how {@link java.util.PriorityQueue} itself is implemented.
 *
 * <h2>Default ordering</h2>
 * The no-arg constructor uses natural ordering: internally it installs a comparator that casts each
 * element to {@link Comparable} and delegates to {@link Comparable#compareTo}. This mirrors
 * {@link java.util.PriorityQueue}'s own no-arg constructor and pushes the type constraint ("E must
 * be Comparable") to a {@link ClassCastException} at compare time rather than encoding it in the
 * class's type bound — that keeps the class usable with an explicit {@link Comparator} for types
 * that are not {@code Comparable} at all.
 *
 * <h2>Bulk build (heapify)</h2>
 * The {@link #BinaryHeap(Collection, Comparator)} constructor loads all elements into the backing
 * array first, then calls {@code siftDown} starting from the last non-leaf index down to the root.
 * Leaves are already trivially valid heaps (no children to violate the invariant), so only the
 * internal nodes need fixing, and fixing bottom-up means each {@code siftDown} only ever moves
 * elements into already-valid subtrees. That is Floyd's build-heap algorithm, O(n) total — cheaper
 * than n sequential {@link #add} calls, which would cost O(n log n).
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>{@code decreaseKey}</b> — an auxiliary element-to-index map turns "find and re-sift an
 *       arbitrary element" from O(n) into O(log n), which is what Dijkstra's algorithm and a
 *       streaming top-k tracker (see {@code [[topk]]}) both need to update a priority in place.</li>
 *   <li><b>d-ary heap</b> — widen the fan-out from 2 to d children per node: shallower tree (fewer
 *       comparisons on {@code siftUp}) at the cost of more comparisons per {@code siftDown} level;
 *       tunable per workload (Dijkstra with dense graphs often prefers d=4).</li>
 * </ul>
 *
 * @param <E> the element type
 */
public final class BinaryHeap<E> {

    private Object[] elements;
    private int size;
    private final Comparator<? super E> comparator;

    private static final int DEFAULT_CAPACITY = 16;

    /**
     * Creates an empty min-heap using natural ordering.
     *
     * @throws ClassCastException at compare time if an added element does not implement
     *     {@link Comparable}
     */
    public BinaryHeap() {
        this(BinaryHeap.<E>naturalOrderComparator());
    }

    /** Creates an empty heap ordered by {@code cmp} — the "smallest" element under {@code cmp} is the root. */
    public BinaryHeap(Comparator<? super E> cmp) {
        if (cmp == null) {
            throw new IllegalArgumentException("comparator must not be null");
        }
        this.comparator = cmp;
        this.elements = new Object[DEFAULT_CAPACITY];
        this.size = 0;
    }

    /** Builds a heap from {@code items} in O(n) via bottom-up heapify (Floyd's algorithm), ordered by {@code cmp}. */
    public BinaryHeap(Collection<? extends E> items, Comparator<? super E> cmp) {
        if (cmp == null) {
            throw new IllegalArgumentException("comparator must not be null");
        }
        this.comparator = cmp;
        this.elements = items.toArray(new Object[0]);
        this.size = elements.length;
        if (this.elements.length == 0) {
            this.elements = new Object[DEFAULT_CAPACITY];
        }
        for (int i = parentOf(size - 1); i >= 0; i--) {
            siftDown(i);
        }
    }

    /** Comparator casting each element to {@link Comparable} — backs the no-arg (natural-ordering) constructor. */
    @SuppressWarnings("unchecked")
    private static <T> Comparator<T> naturalOrderComparator() {
        return (a, b) -> ((Comparable<T>) a).compareTo(b);
    }

    /** Inserts {@code e}, then restores the heap invariant with sift-up. Amortised O(log n). */
    public void add(E e) {
        ensureCapacity(size + 1);
        elements[size] = e;
        size++;
        siftUp(size - 1);
    }

    /** Returns (without removing) the root element, or {@code null} if the heap is empty. O(1). */
    @SuppressWarnings("unchecked")
    public E peek() {
        return size == 0 ? null : (E) elements[0];
    }

    /**
     * Removes and returns the root element, or {@code null} if the heap is empty. The last element
     * takes the root's place and sift-down restores the invariant. O(log n).
     */
    @SuppressWarnings("unchecked")
    public E poll() {
        if (size == 0) {
            return null;
        }
        E root = (E) elements[0];
        size--;
        elements[0] = elements[size];
        elements[size] = null;
        if (size > 0) {
            siftDown(0);
        }
        return root;
    }

    /** Number of elements currently in the heap. */
    public int size() {
        return size;
    }

    /** {@code true} iff the heap holds no elements. */
    public boolean isEmpty() {
        return size == 0;
    }

    @SuppressWarnings("unchecked")
    private int compare(int i, int j) {
        return comparator.compare((E) elements[i], (E) elements[j]);
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = parentOf(i);
            if (compare(i, parent) >= 0) {
                break;
            }
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            int smallest = i;
            if (left < size && compare(left, smallest) < 0) {
                smallest = left;
            }
            if (right < size && compare(right, smallest) < 0) {
                smallest = right;
            }
            if (smallest == i) {
                break;
            }
            swap(i, smallest);
            i = smallest;
        }
    }

    private static int parentOf(int i) {
        return (i - 1) / 2;
    }

    private void swap(int i, int j) {
        Object tmp = elements[i];
        elements[i] = elements[j];
        elements[j] = tmp;
    }

    private void ensureCapacity(int required) {
        if (required <= elements.length) {
            return;
        }
        int newCapacity = elements.length == 0 ? DEFAULT_CAPACITY : elements.length * 2;
        while (newCapacity < required) {
            newCapacity *= 2;
        }
        Object[] grown = new Object[newCapacity];
        System.arraycopy(elements, 0, grown, 0, size);
        elements = grown;
    }
}
