package org.kata.lockfree;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generic lock-free FIFO queue implementing the Michael &amp; Scott algorithm (1996).
 *
 * <p>This is the algorithm behind {@link java.util.concurrent.ConcurrentLinkedQueue}. It is the
 * canonical lock-free queue and a fixture in Java concurrency interviews because it illustrates
 * three ideas beyond what Treiber's stack shows:
 * <ol>
 *   <li><b>Dummy (sentinel) node</b> — a permanently-present node that separates the head and
 *       tail pointers, eliminating the special case where the queue transitions between empty
 *       and non-empty. Without a dummy node, enqueue and dequeue would race on the same node
 *       when the queue has exactly one element.</li>
 *   <li><b>Two-CAS enqueue</b> — inserting an element requires two separate CAS operations: one
 *       to append the new node to the tail's next pointer, and a second to swing the tail pointer
 *       forward. The two CASes cannot be combined into one because they touch different memory
 *       locations. This creates a window where the data structure is in an intermediate state
 *       (tail lags behind the true end of the chain).</li>
 *   <li><b>Cooperative helping</b> — any thread that observes a lagging tail (tail.next != null)
 *       helps the slow thread finish by advancing tail before doing its own work. This is what
 *       makes the algorithm lock-free: even if the thread that started an enqueue is preempted
 *       after the first CAS but before the second, another thread will complete the swing. No
 *       operation can be stuck forever behind a single suspended thread.</li>
 * </ol>
 *
 * <h2>The dummy-node invariant</h2>
 * At all times:
 * <ul>
 *   <li>{@code head} points to a node whose {@code item} is ignored (the sentinel). The true
 *       first element is {@code head.get().next.get()}.</li>
 *   <li>{@code tail} points to the last node in the chain, or to {@code head} when the queue
 *       is empty. Tail may occasionally lag by one node (during an in-progress enqueue).</li>
 * </ul>
 * This invariant means that {@code head == tail &amp;&amp; head.next == null} is the canonical
 * empty check: both pointers on the dummy and nothing appended yet. It also means a dequeue
 * never needs to set tail (only head), and an enqueue never needs to read head — the two
 * operations are almost entirely decoupled.
 *
 * <h2>Why not synchronized?</h2>
 * A single lock on the queue would serialize all producers and consumers. The Michael &amp; Scott
 * algorithm allows an unbounded number of producers and consumers to make progress simultaneously:
 * at most one thread pays for each CAS attempt, and losers simply retry on a fresh snapshot.
 * The two-lock queue (one for head, one for tail) is a middle ground also described in the
 * original paper — it lets one producer and one consumer proceed in parallel — but the fully
 * lock-free version scales to arbitrary concurrency.
 *
 * <h2>Why two AtomicReferences on the next pointer?</h2>
 * Making {@code next} an {@link AtomicReference} rather than a plain {@code volatile} field
 * allows us to CAS from null to a new node atomically. That CAS is the linearization point of
 * enqueue: the moment it succeeds, the new element is "logically" in the queue, visible to all
 * future dequeues. A plain volatile field would require external synchronization for the null-to-value
 * transition.
 *
 * @param <E> the type of elements held in this queue; must not be null.
 * @see java.util.concurrent.ConcurrentLinkedQueue for the production implementation
 * @see TreiberStack for the simpler lock-free stack (single CAS per operation)
 */
public class MichaelScottQueue<E> {

    /**
     * Singly-linked list node. The {@code next} field is an {@link AtomicReference} so it can
     * be CAS'd from {@code null} to a new node during enqueue — the linearization point.
     * {@code item} is plain final; it is written once in the constructor and never changed.
     */
    private static final class Node<E> {
        /** The payload. Null only for the sentinel/dummy node. */
        final E item;

        /**
         * Link to the next node. Starts null (end of chain). CAS'd to a new node during enqueue.
         * Because this is an AtomicReference, reads and writes have volatile semantics: a thread
         * that successfully CASes next will have all its preceding writes visible to any thread
         * that subsequently reads next.
         */
        final AtomicReference<Node<E>> next = new AtomicReference<>(null);

        Node(E item) {
            this.item = item;
        }
    }

    /**
     * Points to the sentinel (dummy) node. The true head of the queue is {@code head.get().next.get()}.
     * Moves forward on each successful dequeue — the old dummy is discarded and the former first
     * real node becomes the new dummy.
     */
    private final AtomicReference<Node<E>> head;

    /**
     * Points to the last node in the chain (or to the sentinel when empty). May lag by one
     * node during an in-progress enqueue; any thread that observes this lag will help advance
     * tail before doing its own work.
     */
    private final AtomicReference<Node<E>> tail;

    /**
     * Constructs an empty queue. Both head and tail start pointing to a shared dummy node
     * whose item is null. This establishes the invariant from the first moment.
     */
    public MichaelScottQueue() {
        Node<E> dummy = new Node<>(null);
        head = new AtomicReference<>(dummy);
        tail = new AtomicReference<>(dummy);
    }

    // ---- Public API ----

    /**
     * Appends {@code item} to the tail of the queue.
     *
     * <p>The enqueue algorithm has two CAS phases:
     * <ol>
     *   <li><b>Append CAS</b>: attempt to CAS the tail node's {@code next} pointer from null
     *       to the new node. This is the linearization point — the instant this CAS succeeds
     *       the element is logically in the queue, even though {@code tail} still points to
     *       the old last node.</li>
     *   <li><b>Swing CAS</b>: attempt to CAS the {@code tail} reference from the old last node
     *       to the new node. This may be done by the thread that won the append CAS, or by
     *       any helper thread that discovers a lagging tail (tail.next != null). It is not a
     *       problem if this CAS fails: it means another thread has already helped advance tail.</li>
     * </ol>
     *
     * <p>If we discover that {@code tail.next} is already non-null (another thread has appended
     * but not yet swung the tail pointer), we help that thread by advancing tail before retrying
     * our own append. This helping is what guarantees lock-freedom: no enqueue is stuck waiting
     * for a specific thread.
     *
     * @param item the item to enqueue; must not be null.
     * @throws NullPointerException if {@code item} is null.
     */
    public void enqueue(E item) {
        if (item == null) throw new NullPointerException("item must not be null");

        Node<E> newNode = new Node<>(item);

        // Outer loop: retry until our append CAS succeeds.
        while (true) {
            // (1) Snapshot the tail and tail's next pointer.
            //     Volatile reads: we see all writes committed before the CAS that installed
            //     the current tail or tail.next.
            Node<E> curTail = tail.get();
            Node<E> tailNext = curTail.next.get();

            // Consistency check: is curTail still the tail we read? If another thread changed
            // tail between our two reads, we have an inconsistent snapshot — restart.
            if (curTail != tail.get()) {
                continue; // snapshot is stale; re-read from the top
            }

            if (tailNext != null) {
                // tailNext != null means another thread has appended a node but not yet swung
                // the tail pointer. Help that thread: advance tail to tailNext, then retry.
                // After this CAS we loop back and re-snapshot a (now fresher) tail.
                tail.compareAndSet(curTail, tailNext);
                // We don't check the return value: if the CAS fails, another helper already
                // advanced tail — that's fine, the effect is the same.
            } else {
                // tailNext == null: curTail is truly the last node. Attempt to append our new node.
                // This is the linearization point of this enqueue.
                if (curTail.next.compareAndSet(null, newNode)) {
                    // Append succeeded. Now attempt to swing the tail pointer to newNode.
                    // If this CAS fails, another thread (a helper) has already done it — that's
                    // fine. Either way the enqueue is logically complete.
                    tail.compareAndSet(curTail, newNode);
                    return;
                }
                // Append CAS failed: another thread appended between our tailNext read (null)
                // and our CAS. Loop back and retry — we'll help advance tail on the next
                // iteration if needed, then try our append again.
            }
        }
    }

    /**
     * Removes and returns the head element of the queue, or {@link Optional#empty()} if the
     * queue is empty.
     *
     * <p>The dequeue algorithm:
     * <ol>
     *   <li>Snapshot head, tail, and head.next (the first real element, if any).</li>
     *   <li>If head.next is null and head == tail: the queue is empty. Return empty.</li>
     *   <li>If head == tail but head.next != null: tail is lagging (an enqueue is in progress).
     *       Help advance tail, then retry — the queue is not empty.</li>
     *   <li>Otherwise (head != tail or head.next != null): read the value from head.next, then
     *       CAS head from the current dummy to head.next, making head.next the new dummy.
     *       Return the value.</li>
     * </ol>
     *
     * <p>The value is read from {@code head.next} (the first real node), not from {@code head}
     * (the dummy). After the CAS, the old head.next becomes the new dummy, and its {@code item}
     * field is effectively ignored by future dequeues. This is fine because the value was already
     * captured before the CAS.
     *
     * @return an Optional containing the dequeued item, or Optional.empty() if empty.
     */
    public Optional<E> dequeue() {
        while (true) {
            // (1) Snapshot head, tail, and head.next.
            Node<E> curHead = head.get();
            Node<E> curTail = tail.get();
            Node<E> headNext = curHead.next.get();

            // Consistency check: if head changed between our reads, restart.
            if (curHead != head.get()) {
                continue;
            }

            if (curHead == curTail) {
                // Head and tail point to the same node.
                if (headNext == null) {
                    // head.next is null: the queue is truly empty (only the dummy node exists).
                    return Optional.empty();
                }
                // head.next != null: an enqueue is in progress but tail hasn't been swung yet.
                // Help that thread advance tail so the queue reaches a consistent state.
                tail.compareAndSet(curTail, headNext);
                // Loop and retry dequeue — after helping, head != tail and we can proceed.
            } else {
                // head != tail: there is at least one real element in the queue.
                // headNext is the first real node. Its item is what we return.
                // We must read the value BEFORE the CAS because after CAS curHead is detached
                // and another thread might enqueue into headNext's next, or GC could in theory
                // reclaim curHead (though here headNext holds the value, not curHead).
                E value = headNext.item;

                // Attempt to advance head: make headNext the new dummy.
                // On success, curHead (the old dummy) is no longer reachable from the queue
                // and will be GC'd. The CAS is the linearization point of this dequeue.
                if (head.compareAndSet(curHead, headNext)) {
                    return Optional.of(value);
                }
                // CAS failed: another thread dequeued before us. Retry.
            }
        }
    }

    /**
     * Returns {@code true} if the queue contains no elements.
     *
     * <p>The empty condition is: head and tail both point to the same node (the dummy), and
     * that node's next is null. This is a point-in-time snapshot; the queue may become
     * non-empty immediately after this method returns.
     *
     * <p>Note: we snapshot head first and read its next, then verify head did not change.
     * If head changed it means a dequeue occurred — the queue is not empty — but we may
     * return a slightly stale true in that window. This is acceptable for isEmpty(), which
     * documents non-linearizability for this read-only method.
     */
    public boolean isEmpty() {
        Node<E> curHead = head.get();
        // Queue is empty iff head == tail (pointing to the dummy) AND head.next == null.
        // We check head.next directly: if head's next is non-null there is a real element
        // even if tail hasn't been swung yet.
        return curHead.next.get() == null && curHead == tail.get();
    }
}
