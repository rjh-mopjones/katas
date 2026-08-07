package org.kata.lease;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A fixed-size, single-threaded pool of lendable resources, handed out as {@link Lease}s.
 *
 * <p>The shape behind a JDBC connection pool or an object pool, stripped to its single-threaded
 * core: a caller {@link #acquire() acquires} a lease, uses {@link Lease#get()} to reach the
 * resource, and returns it by {@link Lease#close() closing} the lease — almost always via
 * try-with-resources, so the return happens even when the caller's code throws:
 * <pre>{@code
 * try (Lease<Connection> lease = pool.acquire()) {
 *     use(lease.get());
 * } // lease.close() runs here, even on exception — the resource always comes back
 * }</pre>
 *
 * <h2>Lazy creation, not pre-warming</h2>
 * The pool does not call {@code factory} {@code size} times at construction. It creates a
 * resource only when a caller acquires and no previously-returned resource is idle and waiting.
 * This mirrors {@code ConnectionPool} ({@code [[connectionpool]]}) but without the concurrency
 * machinery: no {@link java.util.concurrent.Semaphore}, no blocking — {@link #acquire()} either
 * succeeds immediately or throws.
 *
 * <h2>Data structure</h2>
 * A {@link Deque} of idle resources plus a {@code leased} counter. {@code available() == size -
 * leased} at all times — that invariant is what the "returns to the pool" tests pin. Reusing an
 * idle resource (rather than always calling {@code factory}) is what makes the pool meaningfully
 * different from "create a new one each time": expensive-to-construct resources amortise their
 * setup cost across leases.
 *
 * <h2>Why {@code acquire()} throws instead of blocking</h2>
 * This is a single-threaded, fundamentals-tier pool: there is no other thread that could ever
 * return a resource while the caller waits, so blocking would deadlock. Throwing
 * {@link IllegalStateException} immediately gives the caller a chance to react (retry later,
 * surface a "no capacity" error) rather than hang forever. A concurrent pool would instead block
 * with a timeout — see {@code ConnectionPool}.
 *
 * <h2>Possible extensions</h2>
 * <ul>
 *   <li><b>Blocking/timeout acquire</b> across threads — the concurrent cousin,
 *       {@code [[connectionpool]]}, swaps the deque + counter for a {@code Semaphore} + a
 *       thread-safe idle queue.</li>
 *   <li><b>LIFO vs FIFO reuse</b> — this pool reuses most-recently-returned first (a stack), which
 *       keeps a small working set hot; a FIFO queue would round-robin resources evenly instead.</li>
 *   <li><b>Validation on reuse</b> — before handing back an idle resource, run it through a
 *       {@code Predicate<R>} and discard+recreate on failure (as {@code ConnectionPool} does).</li>
 * </ul>
 *
 * @param <R> the type of pooled resource.
 */
public final class Pool<R> {

    private final Supplier<R> factory;
    private final int size;
    private final Deque<R> idle = new ArrayDeque<>();
    private int leased;

    public Pool(Supplier<R> factory, int size) {
        this.factory = Objects.requireNonNull(factory, "factory");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        this.size = size;
    }

    /**
     * Hand out a lease on a resource — an idle, previously-returned one if any is waiting,
     * otherwise a freshly-{@code factory}-created one.
     *
     * @throws IllegalStateException if all {@code size} resources are already leased out.
     */
    public Lease<R> acquire() {
        if (leased >= size) {
            throw new IllegalStateException("pool exhausted: all " + size + " resources are leased");
        }
        R resource = idle.isEmpty() ? factory.get() : idle.pop();
        leased++;
        return new Lease<>(resource, this);
    }

    /** Resources currently idle and available to lease — {@code size - } (resources on loan). */
    public int available() {
        return size - leased;
    }

    /** Package-private return path, called exactly once per lease by {@link Lease#close()}. */
    void release(R resource) {
        idle.push(resource);
        leased--;
    }
}
