package org.kata.lease;

import java.util.function.Supplier;

public final class Pool<R> {

    public Pool(Supplier<R> factory, int size) {
        throw new UnsupportedOperationException();
    }

    public Lease<R> acquire() {
        throw new UnsupportedOperationException();
    }

    public int available() {
        throw new UnsupportedOperationException();
    }
}
