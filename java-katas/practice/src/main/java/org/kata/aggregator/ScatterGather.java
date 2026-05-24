package org.kata.aggregator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ScatterGather {

    public ScatterGather() {
        throw new UnsupportedOperationException();
    }

    public ScatterGather(Executor executor) {
        throw new UnsupportedOperationException();
    }

    public <T> CompletableFuture<List<T>> gatherAll(List<Supplier<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    public <T> CompletableFuture<List<T>> gatherAllWithTimeout(
            List<Supplier<T>> tasks, Duration timeout) {
        throw new UnsupportedOperationException();
    }
}
