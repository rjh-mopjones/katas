package org.kata.quotecache;

import java.util.Optional;
import java.util.function.LongSupplier;

public final class QuoteCache<K, V> {

    public QuoteCache(long ttlNanos) {
        throw new UnsupportedOperationException();
    }

    public QuoteCache(long ttlNanos, LongSupplier clock) {
        throw new UnsupportedOperationException();
    }

    public void put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    public Optional<V> get(K key) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        throw new UnsupportedOperationException();
    }

    public boolean containsKey(K key) {
        throw new UnsupportedOperationException();
    }
}
