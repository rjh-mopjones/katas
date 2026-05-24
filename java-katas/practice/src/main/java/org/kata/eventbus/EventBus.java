package org.kata.eventbus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {

    public <T> Subscription subscribe(Class<T> type, Consumer<T> handler) {
        throw new UnsupportedOperationException();
    }

    public void publish(Object event) {
        throw new UnsupportedOperationException();
    }
}
