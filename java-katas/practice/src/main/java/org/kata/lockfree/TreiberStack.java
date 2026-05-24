package org.kata.lockfree;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generic lock-free stack based on Treiber's algorithm (R. K. Treiber, 1986).
 *
 * <p>This is the simplest correct lock-free data structure and the canonical first example in any
 * concurrency course. It is worth knowing cold for interviews because it demonstrates:
 * <ol>
 *   <li><b>CAS on a single pointer</b> — the entire stack state is captured by the {@code head}
 *       reference; changing it atomically is sufficient to make push and pop linearizable.</li>
 *   <li><b>Immutable nodes</b> — once a {@link Node} is constructed its fields never change.
 *       This is what makes the CAS sound: a thread can safely read {@code node.next} without
 *       a lock because no other thread will ever write to it.</li>
 *   <li><b>Lock-freedom vs wait-freedom</b> — at least one thread always makes progress per
 *       round of CAS attempts (lock-free), but an individual thread can theoretically retry
 *       indefinitely under adversarial scheduling (not wait-free). In practice retry counts
 *       are extremely low because each retry takes only a few nanoseconds.</li>
 *   <li><b>The ABA problem</b> — a latent hazard explained below and solved in
 *       {@link AtomicStampedStack}.</li>
 * </ol>
 *
 * <h2>CAS-loop structure — the universal pattern</h2>
 * Every mutating operation follows the same three-step loop:
 * <pre>
 *   while (true) {
 *       T snapshot = ref.get();            // (1) stable snapshot
 *       T proposed = f(snapshot);          // (2) compute next state — pure, no side-effects
 *       if (ref.compareAndSet(snapshot, proposed)) return; // (3) commit atomically, or retry
 *   }
 * </pre>
 * Step (1) establishes a happens-before edge: the {@code AtomicReference.get()} performs a
 * volatile read, so all writes that preceded the winning CAS are visible to the reading thread.
 * Step (3) is a volatile write (with an implicit fence on x86/ARM); any thread that subsequently
 * reads the reference will see all work done before the CAS. This is how lock-free code achieves
 * correct visibility without {@code synchronized}.
 *
 * <h2>Why not synchronized?</h2>
 * A monitor on the stack would serialize all push/pop operations. Under high concurrency threads
 * queue for the lock (kernel transition, context-switch overhead, possible priority inversion).
 * The CAS approach stays entirely in user-space; on x86 a {@code CMPXCHG} is a single instruction.
 * Loser threads re-do at most a pointer read and a node allocation — microseconds, not
 * microseconds-plus-OS-scheduling-latency.
 *
 * <h2>The ABA problem and why it lurks here</h2>
 * Imagine a free-list variant where popped nodes are recycled:
 * <ol>
 *   <li>Thread T1 reads {@code head = A}, prepares to pop. A.next = B.</li>
 *   <li>T1 is preempted. T2 pops A, pops B, pushes A back. Now {@code head = A} again,
 *       but {@code A.next} has been changed to point somewhere else entirely (B is gone).</li>
 *   <li>T1 resumes. Its CAS({@code A}, {@code A.next=B}) succeeds — the reference matches —
 *       but it writes a stale {@code B} that no longer represents the true top-of-stack.
 *       The stack is silently corrupted.</li>
 * </ol>
 * <b>In this implementation ABA is not a practical problem</b> because the JVM's garbage
 * collector keeps live nodes alive and each push allocates a fresh {@code Node} object.
 * A node can never be returned to head with the same identity unless it is still reachable,
 * i.e. still legitimately on the stack. However, any design that introduces a node free-list
 * (e.g. for off-heap allocation or object-pool optimizations) would immediately expose the bug.
 * See {@link AtomicStampedStack} for the stamp-based solution.
 *
 * <h2>Exact O(1) size is hard to provide lock-free</h2>
 * A size counter would require atomically updating both the counter and the head pointer in the
 * same CAS, which is not possible with a single {@link AtomicReference}. A separate
 * {@link java.util.concurrent.atomic.AtomicInteger} size counter would be consistent only if
 * incremented/decremented inside the same CAS that updates head — impossible. You could use a
 * separate counter with relaxed consistency (approximate size), but that is rarely useful
 * in practice. This implementation omits size() and provides only {@link #isEmpty()}, which
 * is a single volatile read and always accurate at the point it is called.
 *
 * @param <E> the type of elements held in this stack.
 * @see AtomicStampedStack for the ABA-safe version using AtomicStampedReference
 */
public class TreiberStack<E> {

    /**
     * Singly-linked list node. Intentionally immutable: both fields are set once in the
     * constructor and never changed. Immutability is not just a style choice — it is what
     * makes the CAS correct. If {@code next} were mutable, a concurrent writer could change
     * it between the time we snapshot the head and the time we attempt the CAS, silently
     * threading through an inconsistent state.
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
     * The stack's entire mutable state is this single reference. A volatile read/write of
     * this field provides the necessary memory-model ordering: any writes made before a
     * winning CAS (the push of a node) are visible to any thread that subsequently reads
     * the reference (the next pop or push snapshot).
     */
    private final AtomicReference<Node<E>> head = new AtomicReference<>(null);

    // ---- Public API ----

    /**
     * Pushes {@code item} onto the top of the stack.
     *
     * <p>CAS loop:
     * <ol>
     *   <li>Read the current head (snapshot). This is a volatile read; we observe a
     *       consistent view of the chain reachable from head.</li>
     *   <li>Allocate a new node whose {@code next} pointer is the snapshotted head. The
     *       node is built before the CAS attempt; if the CAS fails we simply throw it away
     *       and allocate another. The JVM's young-generation GC makes short-lived allocation
     *       extremely cheap — typically a pointer bump with no locking.</li>
     *   <li>CAS head from the snapshot to the new node. If this succeeds we have atomically
     *       linked the new node to the top of the stack. If it fails, another thread changed
     *       head while we were constructing the new node — we restart from step 1 with the
     *       fresher head.</li>
     * </ol>
     *
     * @param item the item to push; must not be null.
     * @throws NullPointerException if {@code item} is null.
     */
    public void push(E item) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Removes and returns the top item of the stack, or {@link Optional#empty()} if the
     * stack is empty.
     *
     * <p>We return {@link Optional} rather than {@code null} to make the empty case explicit
     * at the call site — callers cannot accidentally skip a null-check. (If you need
     * performance-critical code that avoids Optional allocation, a contract of returning
     * {@code null} for empty is also conventional for lock-free stacks, but Optional is
     * cleaner for interview demonstrations.)
     *
     * <p>CAS loop:
     * <ol>
     *   <li>Snapshot head. If null the stack is empty — return immediately without a CAS.</li>
     *   <li>Read the next node from the snapshot. Because Node is immutable this read is
     *       always safe — oldHead.next is set in the constructor and never changes.</li>
     *   <li>CAS head from oldHead to oldHead.next. On success the old head node is detached
     *       (no references from the stack chain), and will be GC'd as soon as our local
     *       variable {@code oldHead} goes out of scope. On failure, retry.</li>
     * </ol>
     *
     * @return an Optional containing the popped item, or Optional.empty() if the stack was empty.
     */
    public Optional<E> pop() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    /**
     * Returns {@code true} if the stack contains no elements.
     *
     * <p>This is a single volatile read of the head pointer and is always accurate at
     * the moment of the call. The value may change immediately afterwards in a concurrent
     * setting; use it for monitoring or after-the-fact assertions, not for conditional logic
     * that assumes the stack remains in that state.
     */
    public boolean isEmpty() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
