package org.kata.lease;

/**
 * A handle on a single resource borrowed from a {@link Pool}, returned by closing it.
 *
 * <p>{@link #close()} is where the pedagogy lives: it must be safe to call from a
 * try-with-resources block that unwinds due to an exception (the resource still comes back), and
 * it must be <b>idempotent</b> — calling it twice must not return the resource to the pool twice.
 * Without that guard, a caller who (accidentally or defensively) closes a lease more than once
 * would inflate {@link Pool#available()} beyond the pool's real capacity, letting two callers
 * later believe they each hold an exclusive resource that is actually the same object.
 *
 * <h2>Why idempotent, not throw-on-double-close</h2>
 * {@link java.io.Closeable} and {@link AutoCloseable} both document that implementations
 * <em>should</em> tolerate a second {@code close()} as a no-op — nested try-with-resources and
 * manual "belt and braces" cleanup code routinely call {@code close()} more than once. Throwing
 * on the second call would turn defensive cleanup code into a bug source; a silent no-op is the
 * contract callers actually expect.
 *
 * <h2>What close() does <em>not</em> do</h2>
 * It does not call {@code close()} on the underlying resource {@code R} — the pool owns the
 * resource's lifecycle, not the lease. A lease closing means "I am done borrowing this", not
 * "destroy this". A resource that itself needs cleanup on shutdown (e.g. a
 * {@link java.io.Closeable} network connection) is closed by whoever eventually retires the pool,
 * not by every lease that borrows and returns it.
 *
 * @param <R> the type of the leased resource.
 */
public final class Lease<R> implements AutoCloseable {

    private final R resource;
    private final Pool<R> pool;
    private boolean closed;

    Lease(R resource, Pool<R> pool) {
        this.resource = resource;
        this.pool = pool;
    }

    /** The borrowed resource. Still returns the resource after {@link #close()} — the pool is what forgets it. */
    public R get() {
        return resource;
    }

    /** Return the resource to the pool. Idempotent: a second (or later) call is a no-op. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pool.release(resource);
    }
}
