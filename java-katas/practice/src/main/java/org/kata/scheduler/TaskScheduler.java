package org.kata.scheduler;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TaskScheduler implements AutoCloseable {

    static final class ScheduledTask implements Delayed {

        public void cancel() {
            throw new UnsupportedOperationException();
        }

        public boolean isCancelled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(Delayed other) {
            throw new UnsupportedOperationException();
        }
    }

    public synchronized void start() {
        throw new UnsupportedOperationException();
    }

    public ScheduledTask schedule(Runnable action, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void close() {
        throw new UnsupportedOperationException();
    }
}
