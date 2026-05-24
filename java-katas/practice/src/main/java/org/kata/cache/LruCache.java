package org.kata.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LruCache<K, V> implements Cache<K, V> {

    public LruCache(int capacity) {
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
