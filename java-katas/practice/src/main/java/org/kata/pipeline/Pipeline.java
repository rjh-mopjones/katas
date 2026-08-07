package org.kata.pipeline;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class Pipeline<T> {

    public static <T> Pipeline<T> create() {
        throw new UnsupportedOperationException();
    }

    public Pipeline<T> addAll(Collection<? extends T> items) {
        throw new UnsupportedOperationException();
    }

    public <R> Pipeline<R> map(Function<? super T, ? extends R> fn) {
        throw new UnsupportedOperationException();
    }

    public void drainTo(Collection<? super T> sink) {
        throw new UnsupportedOperationException();
    }

    public List<T> toList() {
        throw new UnsupportedOperationException();
    }

    public static <T> void copy(List<? super T> dst, List<? extends T> src) {
        throw new UnsupportedOperationException();
    }
}
