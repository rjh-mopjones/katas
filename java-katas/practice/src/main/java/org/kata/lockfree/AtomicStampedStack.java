package org.kata.lockfree;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * Generic lock-free stack using {@link AtomicStampedReference} to defeat the ABA problem.
 *
 * <p>This class is structurally identical to {@link TreiberStack} — push and pop implement the
 * same CAS-loop skeleton — but uses a stamped reference whose stamp is incremented on every
 * successful mutation. The stamp makes it possible to distinguish "node A at generation 3" from
 * "node A at generation 5", so a stale CAS that observes A twice cannot succeed spuriously.
 *
 * <h2>The ABA problem — a concrete scenario</h2>
 * <p>The ABA problem arises whenever a CAS checks only the reference identity (pointer value)
 * of an expected node, but the node has been removed and re-inserted between the snapshot read
 * and the CAS attempt — so the reference still matches but the state it implies does not.
 *
 * <p>Classic free-list scenario (what would happen with a plain {@link TreiberStack} and node
 * pooling, or with off-heap / native memory):
 * <ol>
 *   <li>Stack state: A → B → C. Thread T1 snapshots {@code head = A}, prepares to pop.
 *       A.next = B.</li>
 *   <li>T1 is preempted. Thread T2 pops A (A is removed, head = B → C). T2 then pops B
 *       (head = C). T2 recycles A (e.g. puts A back in a free-list or an object pool) and
 *       pushes A again. Now the stack is A → C, and A.next has been overwritten to point to C,
 *       not B.</li>
 *   <li>T1 resumes. It attempts CAS(expected=A, next=B). The head is still A (same object
 *       identity) so the CAS succeeds. But it installs B as the new head — a node that has
 *       been logically deleted. The stack is now B → ??? (B.next is stale garbage).</li>
 * </ol>
 *
 * <p>This bug is silent: no exception is thrown, but the stack is corrupted. Items may appear
 * twice or disappear. In the JVM, this is mostly avoided by the garbage collector: a popped node
 * that is no longer reachable from any stack chain cannot be pushed back with the same object
 * identity (it would have to survive GC, which requires a reference from somewhere). However:
 * <ul>
 *   <li><b>Object pools / free-lists</b>: a pool holds a reference to the node, keeping it alive.
 *       When it is "pushed" again it has the same identity but logically different content.</li>
 *   <li><b>Off-heap / native memory</b>: addresses can be reused by the allocator with no GC
 *       involvement.</li>
 *   <li><b>Reference Q261</b>: this primer labels the ABA problem as Q261 in its catalogue of
 *       lock-free correctness issues. The stamp solution below is the standard Java answer.</li>
 * </ul>
 *
 * <h2>Stamp-based solution</h2>
 * {@link AtomicStampedReference} pairs a reference with a monotonically-increasing integer
 * stamp in a single atomically-swappable word. The CAS succeeds only if <em>both</em> the
 * reference <em>and</em> the stamp match the expected values. Each successful push or pop
 * increments the stamp by 1. Now in the ABA scenario:
 * <ol>
 *   <li>T1 snapshots {@code (head=A, stamp=3)}.</li>
 *   <li>T2 pops A (stamp becomes 4), pops B (stamp=5), pushes A (stamp=6). The stamp has
 *       advanced three times.</li>
 *   <li>T1 attempts CAS(expectedRef=A, expectedStamp=3, newRef=B, newStamp=4). The stamp is
 *       now 6, not 3. The CAS fails. T1 retries with the fresh snapshot (A, stamp=6) and
 *       discovers the correct state.</li>
 * </ol>
 *
 * <h2>Alternatives to AtomicStampedReference</h2>
 * <ul>
 *   <li><b>{@link java.util.concurrent.atomic.AtomicMarkableReference}</b>: pairs a reference
 *       with a single boolean mark bit. Useful for logical deletion (mark-and-sweep linked lists)
 *       but not sufficient to defeat ABA — the mark can cycle back to its original value.</li>
 *   <li><b>Hazard pointers (Michael 2004)</b>: each thread declares which node it is currently
 *       reading. A reclaimer checks all hazard pointers before freeing a node. Avoids the stamp
 *       overflow concern but adds significant bookkeeping overhead; more common in C/C++.</li>
 *   <li><b>Epoch-based reclamation (EBR)</b>: threads announce their current epoch; nodes are
 *       freed only when no thread is in an old epoch. Lower overhead than hazard pointers but
 *       requires careful epoch management. Used in some JVM runtimes for off-heap structures.</li>
 *   <li><b>GC-managed languages</b>: as noted above, the JVM's GC prevents ABA in most
 *       practical cases because live nodes cannot be recycled. The stamp approach here is
 *       primarily valuable as a teaching tool and for object-pool / off-heap scenarios.</li>
 * </ul>
 *
 * <h2>Stamp overflow</h2>
 * The stamp is a 32-bit {@code int}. At one successful mutation per nanosecond it wraps in
 * approximately 4.3 seconds. In practice, operations take tens of nanoseconds or more, so
 * wrap-around is not a concern for interview-scale usage. Production systems that need stronger
 * guarantees use 64-bit stamps or hazard pointers.
 *
 * @param <E> the type of elements held in this stack.
 * @see TreiberStack for the same algorithm without ABA protection
 * @see AtomicStampedReference for the JDK class powering this implementation
 */
public class AtomicStampedStack<E> {

    /**
     * Immutable singly-linked list node. Immutability is required for the same reason as in
     * {@link TreiberStack}: once a node is installed in the chain its {@code next} pointer must
     * never change, otherwise a concurrent reader could observe an inconsistent chain.
     */
    private static final class Node<E> {
        final E item;
        final Node<E> next;

        Node(E item, Node<E> next) {
            this.item = item;
            this.next = next;
        }
    }

    /**
     * Pairs the head node reference with a monotonically-increasing stamp. Both are swapped
     * atomically by compareAndSet. The stamp is stored in the high 32 bits of a long on
     * HotSpot (implementation detail), but the JDK API exposes it as two separate ints.
     *
     * <p>Initial stamp is 0. Each successful push or pop increments it by 1.
     */
    private final AtomicStampedReference<Node<E>> head =
            new AtomicStampedReference<>(null, 0);

    // ---- Public API ----

    /**
     * Pushes {@code item} onto the top of the stack.
     *
     * <p>The loop reads both the current head node and the current stamp in a single
     * {@link AtomicStampedReference#get(int[])} call to ensure the two values are consistent
     * (they were atomically installed together by the previous winning CAS). The new stamp is
     * {@code oldStamp + 1}, ensuring that every successful mutation advances the stamp and
     * makes any stale snapshot distinguishable.
     *
     * @param item the item to push; must not be null.
     * @throws NullPointerException if {@code item} is null.
     */
    public void push(E item) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Removes and returns the top item, or {@link Optional#empty()} if the stack is empty.
     *
     * <p>The stamp ensures that a thread which wakes up after a long preemption cannot
     * mistake a recycled node for the one it snapshotted: if the stack was popped and the
     * same node was pushed back (ABA), the stamp will have advanced and the CAS will fail.
     *
     * @return an Optional containing the popped item, or Optional.empty() if empty.
     */
    public Optional<E> pop() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Returns {@code true} if the stack contains no elements.
     *
     * <p>This is a volatile read of the reference portion of the stamped pair. The stamp is
     * not needed for an isEmpty check because null/non-null is unambiguous even without
     * generational information.
     */
    public boolean isEmpty() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
