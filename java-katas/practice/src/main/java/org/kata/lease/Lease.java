package org.kata.lease;

public final class Lease<R> implements AutoCloseable {

    public R get() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }
}
