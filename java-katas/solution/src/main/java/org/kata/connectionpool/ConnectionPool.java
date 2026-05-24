package org.kata.connectionpool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A generic, bounded, lazy-initialising resource pool.
 *
 * <p>The canonical example is a JDBC connection pool, but the same structure applies to any
 * expensive reusable resource: HTTP clients, gRPC channels, file handles, thread-local
 * encryption contexts.
 *
 * <h2>Why connection pools exist</h2>
 * Creating a connection is expensive: TCP handshake, TLS negotiation, auth exchange — often
 * tens to hundreds of milliseconds. A pool amortises that cost by keeping connections alive and
 * reusing them across requests. The cost of not having a pool at scale is severe: under high
 * concurrency, every request would block on connection setup and the server would exhaust OS
 * socket limits.
 *
 * <h2>How many connections? (HikariCP sizing heuristic)</h2>
 * HikariCP's documentation (which cites PostgreSQL's wiki) suggests:
 * <pre>
 *   pool_size = (core_count × 2) + effective_spindle_count
 * </pre>
 * For a 4-core machine with SSDs (effective_spindle_count ≈ 1): {@code pool_size ≈ 9}.
 * The intuition: each CPU core can context-switch between two threads waiting on I/O plus
 * one thread actually computing. Beyond this point, adding more connections does NOT increase
 * throughput — it only increases memory pressure, context-switching overhead, and database-side
 * connection management cost. Bigger pools are almost always slower, not faster.
 *
 * <h2>Design choices</h2>
 * <ul>
 *   <li><b>Semaphore for permit counting.</b> A {@link Semaphore} models "number of free slots"
 *       cleanly: {@code acquire()} blocks until a slot is available (or times out), and
 *       {@code release()} returns the slot. This separates the "may I proceed" decision from
 *       the "which resource do I get" lookup — the semaphore enforces the bound; the queue
 *       tracks which specific resources are currently idle.</li>
 *   <li><b>ConcurrentLinkedQueue for idle resources.</b> An unbounded lock-free queue for
 *       the pool of idle resources. {@code poll()} is O(1) and non-blocking; if it returns null
 *       after the semaphore has been acquired, that means a new resource should be created (up
 *       to maxSize). Once maxSize resources exist, poll() will always return a non-null resource
 *       for any successful acquire.</li>
 *   <li><b>Lazy initialisation.</b> Resources are created on first borrow rather than at pool
 *       construction time. This avoids the overhead of pre-creating all connections when the
 *       application starts and load is low.</li>
 *   <li><b>Validation on borrow.</b> Before handing out an idle resource, the pool runs it
 *       through an optional {@code Predicate<R> validator}. Resources that fail validation
 *       (e.g., a closed database connection) are discarded and a new one is created in their
 *       place. This keeps the pool self-healing without requiring an external health-check
 *       thread.</li>
 *   <li><b>Null return on timeout.</b> {@link #borrow} returns {@code null} when the timeout
 *       elapses rather than throwing. This lets callers decide whether to retry, return an
 *       error, or serve from a fallback — without paying the cost of exception construction
 *       and stack-walking in the hot path. Callers who prefer an exception can wrap the call
 *       site trivially.</li>
 * </ul>
 *
 * <h2>What this omits (production concerns)</h2>
 * A production pool (e.g. HikariCP, Apache DBCP) also provides:
 * <ul>
 *   <li>Maximum connection lifetime to prevent stale TCP sessions.</li>
 *   <li>Idle connection eviction to return resources to the database during quiet periods.</li>
 *   <li>Connection keep-alive pings.</li>
 *   <li>Metrics and JMX reporting.</li>
 *   <li>Statement caching (JDBC-specific).</li>
 * </ul>
 *
 * @param <R> the type of the pooled resource.
 */
public class ConnectionPool<R> {

    // ---- Configuration ----

    private final Supplier<R> factory;
    private final Predicate<R> validator;
    private final int maxSize;

    // ---- State ----

    /**
     * Idle resources waiting to be borrowed. ConcurrentLinkedQueue is lock-free and perfectly
     * sized for this use-case: unbounded (capacity handled by the semaphore), fast poll/offer.
     */
    private final ConcurrentLinkedQueue<R> idle = new ConcurrentLinkedQueue<>();

    /**
     * Permits = number of resources that may be borrowed concurrently. Starts at maxSize.
     * {@code acquire()} blocks until a permit is available; {@code release()} returns one.
     *
     * <p>The semaphore enforces the pool's size bound. Without it we would need to count
     * in-flight borrows ourselves, coordinate that count with the idle queue, and block
     * callers manually — all of which the Semaphore already implements correctly.
     */
    private final Semaphore semaphore;

    /**
     * Total number of resources ever created. Used to distinguish "idle queue is empty because
     * all maxSize resources are in use" from "idle queue is empty because we haven't created
     * any yet" — in the latter case we must create a new resource.
     */
    private final AtomicInteger totalCreated = new AtomicInteger(0);

    // ---- Constructors ----

    /**
     * Convenience constructor with an always-valid resource (no validation).
     *
     * @param factory  creates a new resource on demand; must not return null.
     * @param maxSize  maximum number of concurrently borrowed resources; must be ≥ 1.
     */
    public ConnectionPool(Supplier<R> factory, int maxSize) {
        this(factory, maxSize, r -> true);
    }

    /**
     * @param factory    creates a new resource on demand; must not return null.
     * @param maxSize    maximum number of resources that may be borrowed at once; must be ≥ 1.
     * @param validator  predicate to test an idle resource before handing it out. Return
     *                   {@code false} to have the pool discard the resource and create a fresh
     *                   one. A typical JDBC validator calls {@code Connection.isValid(1)}.
     */
    public ConnectionPool(Supplier<R> factory, int maxSize, Predicate<R> validator) {
        if (factory == null) throw new IllegalArgumentException("factory must not be null");
        if (maxSize < 1) throw new IllegalArgumentException("maxSize must be >= 1");
        if (validator == null) throw new IllegalArgumentException("validator must not be null");
        this.factory = factory;
        this.maxSize = maxSize;
        this.validator = validator;
        // Non-fair semaphore: fairness (FIFO ordering of waiting threads) is rarely worth the
        // throughput cost in pool scenarios. Threads that time out do not hold any permit, so
        // the non-fair race is safe.
        this.semaphore = new Semaphore(maxSize, false);
    }

    // ---- Public API ----

    /**
     * Borrow a resource from the pool, waiting up to the given timeout if none is immediately
     * available.
     *
     * <p>The caller MUST call {@link #release} when done, even if an exception is thrown while
     * the resource is in use. Failure to release a resource will permanently exhaust the pool.
     * The idiomatic pattern:
     * <pre>{@code
     * R resource = pool.borrow(1, TimeUnit.SECONDS);
     * if (resource == null) { /* handle timeout *\/ }
     * try {
     *     use(resource);
     * } finally {
     *     pool.release(resource);
     * }
     * }</pre>
     *
     * @param timeout maximum time to wait for a resource to become available.
     * @param unit    time unit of {@code timeout}.
     * @return a resource, or {@code null} if the timeout elapsed before one was available.
     * @throws InterruptedException if the calling thread is interrupted while waiting.
     */
    public R borrow(long timeout, TimeUnit unit) throws InterruptedException {
        // Attempt to acquire a permit within the deadline. If this returns false, we timed out.
        if (!semaphore.tryAcquire(timeout, unit)) {
            return null; // Timeout: caller decides how to handle.
        }

        // Permit acquired — we are allowed to hold exactly one resource. Find or create it.
        // This loop handles validator rejection: if an idle resource is stale we discard it
        // and try the next one (or create a fresh resource). We already hold a permit, so we
        // will eventually return a resource or have created one.
        while (true) {
            R resource = idle.poll();

            if (resource == null) {
                // Idle queue is empty: create a new resource. We know totalCreated < maxSize
                // because the semaphore's permit count precisely tracks "slots available for
                // creation or idle use". This increment is the memory-safe proof.
                totalCreated.incrementAndGet();
                return factory.get();
            }

            // Validate the idle resource before handing it out. A stale TCP connection or an
            // expired token would cause the caller's operation to fail immediately — better to
            // detect it here and replace it transparently.
            if (validator.test(resource)) {
                return resource;
            }

            // Invalid resource: discard it. We still hold the permit, so we loop to get
            // another idle resource or create a fresh one. However, since we discarded one
            // resource, the total "live" count is now one less than maxSize — create a
            // replacement. Decrement totalCreated so the invariant "totalCreated == live
            // resources" holds.
            totalCreated.decrementAndGet();
            // factory.get() creates the replacement; increment totalCreated to reflect it.
            totalCreated.incrementAndGet();
            return factory.get();
        }
    }

    /**
     * Return a previously borrowed resource to the pool, making it available for other callers.
     *
     * <p>Callers must release every resource they borrow — including in {@code finally} blocks.
     * Releasing a resource that was not obtained from this pool, or releasing the same resource
     * twice, produces undefined behaviour (most likely an over-released semaphore that allows
     * more concurrent borrows than {@code maxSize}).
     *
     * @param resource the resource to return; must not be null.
     */
    public void release(R resource) {
        if (resource == null) throw new IllegalArgumentException("resource must not be null");
        // Return to idle queue first, then release the permit. This order matters: if the
        // permit were released first, another thread could acquire it and find the idle queue
        // still empty, causing it to create an extra resource beyond maxSize.
        idle.offer(resource);
        semaphore.release();
    }

    // ---- Monitoring ----

    /**
     * Returns the number of resources currently sitting idle in the pool.
     * Point-in-time snapshot.
     */
    public int available() {
        return idle.size();
    }

    /**
     * Returns the number of resources currently borrowed by callers (i.e., not yet released).
     * Point-in-time snapshot.
     */
    public int inUse() {
        // semaphore.availablePermits() = how many more borrows can proceed without blocking.
        // maxSize - availablePermits = permits currently held = resources in use.
        return maxSize - semaphore.availablePermits();
    }
}
