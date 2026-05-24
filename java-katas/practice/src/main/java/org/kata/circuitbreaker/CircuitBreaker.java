package org.kata.circuitbreaker;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public CircuitBreaker(int failureThreshold, long openDurationNanos, int successThreshold) {
        throw new UnsupportedOperationException();
    }

    public CircuitBreaker(int failureThreshold, long openDurationNanos, int successThreshold,
                          LongSupplier clock) {
        throw new UnsupportedOperationException();
    }

    public State state() {
        throw new UnsupportedOperationException();
    }

    public <T> T call(Callable<T> action) throws Exception {
        throw new UnsupportedOperationException();
    }
}
