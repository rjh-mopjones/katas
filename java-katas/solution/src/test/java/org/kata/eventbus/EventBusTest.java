package org.kata.eventbus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    // Simple domain events for testing — records give us value equality for free.
    record OrderPlaced(int orderId) {}
    record PaymentReceived(int amount) {}

    @Test
    void handler_receives_published_event_of_its_type() {
        var bus = new EventBus();
        List<OrderPlaced> received = new ArrayList<>();

        bus.subscribe(OrderPlaced.class, received::add);
        bus.publish(new OrderPlaced(42));

        assertEquals(1, received.size());
        assertEquals(42, received.get(0).orderId());
    }

    @Test
    void multiple_handlers_for_same_type_all_fire() {
        var bus = new EventBus();
        var counter = new AtomicInteger();

        // Three handlers subscribed to the same type — all must be invoked.
        bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());
        bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());
        bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());
        bus.publish(new OrderPlaced(1));

        assertEquals(3, counter.get(), "all three handlers must fire");
    }

    @Test
    void handlers_are_invoked_in_registration_order() {
        // CopyOnWriteArrayList preserves insertion order; verify the bus honours it.
        var bus = new EventBus();
        List<Integer> order = new ArrayList<>();

        bus.subscribe(OrderPlaced.class, e -> order.add(1));
        bus.subscribe(OrderPlaced.class, e -> order.add(2));
        bus.subscribe(OrderPlaced.class, e -> order.add(3));
        bus.publish(new OrderPlaced(0));

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void unsubscribe_stops_delivery() {
        var bus = new EventBus();
        var counter = new AtomicInteger();

        Subscription sub = bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());
        bus.publish(new OrderPlaced(1));   // counter = 1
        sub.unsubscribe();
        bus.publish(new OrderPlaced(2));   // handler removed — counter stays at 1
        bus.publish(new OrderPlaced(3));

        assertEquals(1, counter.get(), "handler must not fire after unsubscribe");
    }

    @Test
    void unsubscribe_is_idempotent() {
        // Calling unsubscribe multiple times must not throw or have side effects beyond the first.
        var bus = new EventBus();
        var counter = new AtomicInteger();

        Subscription sub = bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());
        sub.unsubscribe();
        assertDoesNotThrow(sub::unsubscribe);  // second call must be a no-op
        assertDoesNotThrow(sub::unsubscribe);  // third call too

        bus.publish(new OrderPlaced(1));
        assertEquals(0, counter.get(), "handler must remain removed after repeated unsubscribe");
    }

    @Test
    void event_with_no_subscribers_is_a_no_op() {
        var bus = new EventBus();
        // Publishing to a type with no registered handlers must not throw.
        assertDoesNotThrow(() -> bus.publish(new PaymentReceived(100)));
    }

    @Test
    void event_of_different_type_is_not_delivered_to_handler() {
        var bus = new EventBus();
        var counter = new AtomicInteger();

        bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());
        // Publish a PaymentReceived — must NOT reach the OrderPlaced handler.
        bus.publish(new PaymentReceived(50));

        assertEquals(0, counter.get(), "handler must not receive events of a different type");
    }

    @Test
    void throwing_handler_does_not_prevent_subsequent_handlers_from_running() {
        // Error isolation: one bad handler must not starve the others.
        var bus = new EventBus();
        var counter = new AtomicInteger();

        bus.subscribe(OrderPlaced.class, e -> { throw new RuntimeException("bad handler"); });
        bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());  // must still run
        bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());  // must still run

        assertDoesNotThrow(() -> bus.publish(new OrderPlaced(1)),
                "publish must not propagate exceptions from handlers");
        assertEquals(2, counter.get(), "healthy handlers must still fire despite the throwing one");
    }

    @Test
    void unsubscribe_removes_only_own_handler_not_others() {
        var bus = new EventBus();
        var counter1 = new AtomicInteger();
        var counter2 = new AtomicInteger();

        Subscription sub1 = bus.subscribe(OrderPlaced.class, e -> counter1.incrementAndGet());
        bus.subscribe(OrderPlaced.class, e -> counter2.incrementAndGet());

        sub1.unsubscribe();
        bus.publish(new OrderPlaced(1));

        assertEquals(0, counter1.get(), "sub1 handler removed — should not fire");
        assertEquals(1, counter2.get(), "sub2 handler still registered — must fire");
    }

    @Test
    void concurrent_publishes_deliver_to_all_handlers() throws Exception {
        // 200 threads publish concurrently; a single handler counts deliveries.
        // CopyOnWriteArrayList's iteration is over a stable snapshot per publish call, so
        // no ConcurrentModificationException and no missed deliveries.
        var bus = new EventBus();
        var counter = new AtomicInteger();
        bus.subscribe(OrderPlaced.class, e -> counter.incrementAndGet());

        int N = 200;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    bus.publish(new OrderPlaced(i));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(N, counter.get(), "every concurrent publish must reach the handler exactly once");
    }

    @Test
    void concurrent_subscribe_and_publish_are_safe() throws Exception {
        // Half the threads subscribe; the other half publish. No exceptions must occur.
        // CopyOnWriteArrayList absorbs concurrent writes; iteration over publish snapshots
        // means a handler added mid-flight may or may not fire for a given publish — that
        // is acceptable (no ordering guarantee across concurrent subscribe + publish).
        var bus = new EventBus();
        // Use a thread-safe list to accumulate received events without synchronisation.
        var received = new CopyOnWriteArrayList<OrderPlaced>();

        int N = 100;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N * 2);

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            // N publishers
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    bus.publish(new OrderPlaced(i));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            // N subscribers
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    bus.subscribe(OrderPlaced.class, received::add);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }
        // We only assert no exception was thrown; the exact number of received events
        // is non-deterministic because subscriptions and publishes race.
    }
}
