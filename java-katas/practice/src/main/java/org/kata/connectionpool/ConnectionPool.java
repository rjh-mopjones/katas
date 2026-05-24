package org.kata.connectionpool;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class ConnectionPool<R> {

    public ConnectionPool(Supplier<R> factory, int maxSize) {
        throw new UnsupportedOperationException();
    }

    public ConnectionPool(Supplier<R> factory, int maxSize, Predicate<R> validator) {
        throw new UnsupportedOperationException();
    }

    public R borrow(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public void release(R resource) {
        throw new UnsupportedOperationException();
    }

    public int available() {
        throw new UnsupportedOperationException();
    }

    public int inUse() {
        throw new UnsupportedOperationException();
    }
}
