package org.kata.retry;

import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;

public class Retryer {

    public Retryer(RetryPolicy policy) {
        throw new UnsupportedOperationException();
    }

    public Retryer(RetryPolicy policy, LongConsumer sleeper, Random random) {
        throw new UnsupportedOperationException();
    }

    public <T> T execute(Callable<T> action) throws Exception {
        throw new UnsupportedOperationException();
    }
}
