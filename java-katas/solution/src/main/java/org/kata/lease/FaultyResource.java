package org.kata.lease;

/**
 * A resource whose {@link #close()} always throws — a fixture for drilling suppressed exceptions.
 *
 * <p>Used directly in a multi-resource try-with-resources block alongside a {@link Lease}: when
 * the try body throws and this resource's {@code close()} also throws while the stack unwinds,
 * the JVM does not discard either exception. The body's exception propagates as the primary one,
 * and this resource's close-time exception is attached to it via
 * {@link Throwable#addSuppressed(Throwable)}, retrievable from {@link Throwable#getSuppressed()}.
 */
public final class FaultyResource implements AutoCloseable {

    @Override
    public void close() {
        throw new IllegalStateException("close always fails for FaultyResource");
    }
}
