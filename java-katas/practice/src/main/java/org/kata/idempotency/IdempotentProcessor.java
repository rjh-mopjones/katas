package org.kata.idempotency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class IdempotentProcessor {

    public <T> T process(String idempotencyKey, Supplier<T> action) {
        throw new UnsupportedOperationException();
    }

    public boolean isProcessed(String idempotencyKey) {
        throw new UnsupportedOperationException();
    }
}
