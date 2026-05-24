package org.kata.lockfree;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicStampedReference;

public class AtomicStampedStack<E> {

    public void push(E item) {
        throw new UnsupportedOperationException();
    }

    public Optional<E> pop() {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }
}
