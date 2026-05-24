package org.kata.cache;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentLruCache<K, V> implements Cache<K, V> {

    public ConcurrentLruCache(int capacity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<V> get(K key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
