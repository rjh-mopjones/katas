package org.kata.cache;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

public class LfuCache<K, V> implements Cache<K, V> {

    public LfuCache(int capacity) {
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
