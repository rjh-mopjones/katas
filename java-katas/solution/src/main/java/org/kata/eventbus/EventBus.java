package org.kata.eventbus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A synchronous, type-based publish/subscribe event bus.
 *
 * <h2>Pattern: Observer (a.k.a. Event Bus / Pub-Sub)</h2>
 * The Observer pattern decouples event producers from consumers. Publishers have no knowledge of
 * who is listening; subscribers register interest in specific event types and are notified
 * automatically. This is the foundation of UI toolkits (AWT/Swing), domain event-driven
 * architectures, and plugin systems.
 *
 * <h2>Type dispatch — exact type, not hierarchy</h2>
 * Dispatch is keyed on {@link Object#getClass()} — the runtime type of the published event.
 * Handlers registered for {@code Animal.class} do <em>not</em> receive a {@code Dog} event.
 * This is simpler to implement and reason about.
 *
 * <p>Guava EventBus, by contrast, walks the type hierarchy (superclasses + interfaces) and
 * delivers to all handlers whose parameter type is assignable from the event's runtime type.
 * This enables polymorphic dispatch but makes the order of invocation and the set of "which
 * handlers fire" harder to predict. For an interview, noting this trade-off demonstrates depth.
 *
 * <h2>Thread safety: ConcurrentHashMap + CopyOnWriteArrayList</h2>
 * <ul>
 *   <li>{@link ConcurrentHashMap} — the outer map (type → handler list) is safe for concurrent
 *       reads and writes without a global lock. Individual bucket locks allow multiple types to
 *       be subscribed/published simultaneously.</li>
 *   <li>{@link CopyOnWriteArrayList} — the handler list for a given type is the right structure
 *       when <em>reads (publishes) heavily outnumber writes (subscribe/unsubscribe)</em>. Every
 *       modification creates a fresh backing array, so iteration during publish never sees a
 *       half-mutated list and never throws {@link java.util.ConcurrentModificationException}.
 *       A plain {@code ArrayList} guarded by {@code synchronized} would work but would block
 *       concurrent publishers; an {@link java.util.concurrent.CopyOnWriteArrayList} allows
 *       concurrent publishes to proceed without synchronisation during iteration.</li>
 * </ul>
 *
 * <h2>Synchronous vs asynchronous dispatch</h2>
 * {@link #publish} invokes handlers on the caller's thread, in registration order. This gives
 * predictable ordering, makes exceptions visible to the publisher, and avoids the overhead of
 * scheduling and thread handoff.
 *
 * <p>The downside: a slow handler delays subsequent handlers and blocks the publisher. For
 * high-throughput systems, or when handlers perform I/O, an <em>asynchronous</em> bus that
 * dispatches to a thread pool or an executor is preferable. You could wrap each handler
 * invocation in {@code executor.submit(() -> handler.accept(event))} to achieve this.
 *
 * <h2>Error isolation</h2>
 * If a handler throws, we catch the exception, log it (or swallow it for the kata), and
 * <em>continue delivering to remaining handlers</em>. The alternative — stopping on the first
 * failure — would let one bad handler silently prevent all subsequent handlers from ever
 * receiving events, which is a subtle and hard-to-debug failure mode. Error isolation is the
 * right default: each handler contract is independent.
 *
 * <h2>Ordering guarantee</h2>
 * Handlers are invoked in the order they were registered (FIFO). {@link CopyOnWriteArrayList}
 * preserves insertion order, so this guarantee holds even across concurrent subscribe calls —
 * concurrent appends are serialised inside COWAL.
 */
public class EventBus {

    /**
     * Internal wrapper pairing a handler with its type token. We store a raw {@code Consumer<Object>}
     * (cast from the typed consumer at subscribe time) to avoid per-dispatch reflection. The
     * type safety is upheld by the outer map key: we only look up handlers for the event's
     * exact runtime type, so the cast in {@link #publish} is always safe.
     */
    private record HandlerEntry<T>(Class<T> type, Consumer<T> handler) {}

    // Map from event type → ordered list of handler entries for that type.
    // ConcurrentHashMap: concurrent subscribe/publish on different types without contention.
    // CopyOnWriteArrayList: iteration (publish) is lock-free; writes (subscribe/unsubscribe)
    // are rare and pay a copy cost — the ideal trade-off for an event bus.
    private final Map<Class<?>, CopyOnWriteArrayList<HandlerEntry<?>>> handlers =
            new ConcurrentHashMap<>();

    /**
     * Subscribe a handler to all future events of type {@code T}.
     *
     * <p>The handler is invoked synchronously on the thread that calls {@link #publish}.
     * Events published before this call are not delivered to this handler (no replay).
     *
     * @param type    the exact event type to listen for; must not be null.
     * @param handler the callback; must not be null. May be called from multiple threads if
     *                multiple threads publish events, so it should be thread-safe or otherwise
     *                handle concurrent invocation.
     * @param <T>     the event type.
     * @return a {@link Subscription} whose {@link Subscription#unsubscribe()} method removes
     *         this handler. The subscription is idempotent — calling unsubscribe multiple times
     *         is safe.
     */
    public <T> Subscription subscribe(Class<T> type, Consumer<T> handler) {
        if (type == null)    throw new IllegalArgumentException("type must not be null");
        if (handler == null) throw new IllegalArgumentException("handler must not be null");

        HandlerEntry<T> entry = new HandlerEntry<>(type, handler);

        // computeIfAbsent atomically initialises the list for a new type. Subsequent
        // subscribe calls for the same type simply append to the existing list.
        CopyOnWriteArrayList<HandlerEntry<?>> list =
                handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        list.add(entry);

        // Return a Subscription token. The token holds a reference to the entry so that
        // unsubscribe can remove exactly this registration, even if the same handler lambda
        // is registered twice (each registration gets its own entry object).
        return new Subscription() {
            private volatile boolean cancelled = false;

            @Override
            public void unsubscribe() {
                // Idempotent: the first call removes the entry; subsequent calls are no-ops.
                if (!cancelled) {
                    cancelled = true;
                    // CopyOnWriteArrayList.remove(Object) uses equals(). HandlerEntry is a record
                    // so equality is structural (type + handler fields). Lambda instances do NOT
                    // implement value equality — each lambda is its own distinct object — so two
                    // registrations of the same lambda reference produce two HandlerEntry objects
                    // that are NOT equal, and remove(entry) removes exactly the one captured by
                    // this closure. This is precisely the correct identity-based removal behaviour.
                    CopyOnWriteArrayList<HandlerEntry<?>> entryList =
                            handlers.get(entry.type());
                    if (entryList != null) entryList.remove(entry);
                }
            }
        };
    }

    /**
     * Publish an event to all handlers registered for its exact runtime type.
     *
     * <p>Handlers are invoked synchronously and in registration order. If a handler throws,
     * the exception is caught and suppressed so that subsequent handlers still receive the event.
     * This error-isolation behaviour prevents one broken handler from silently starving others.
     *
     * <p>If no handlers are registered for {@code event}'s type, this method is a no-op —
     * no exception is thrown. This matches the "fire and forget" semantics of pub/sub: producers
     * do not know (or care) whether there are any subscribers.
     *
     * @param event the event to publish; must not be null. Its type is determined via
     *              {@link Object#getClass()} — the runtime type, not the declared compile-time
     *              type of the reference.
     */
    @SuppressWarnings("unchecked")
    public void publish(Object event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");

        // Look up by exact runtime type. If the caller declares 'Animal a = new Dog()' and
        // passes 'a', getClass() returns Dog.class — dispatch is always on the concrete type.
        List<HandlerEntry<?>> list = handlers.get(event.getClass());
        if (list == null) return;   // no subscribers for this type — fast no-op

        // Iterate over a snapshot. Because list is a CopyOnWriteArrayList, the iteration is
        // over the array that existed at the start of this loop — concurrent subscribe/
        // unsubscribe during publish do not affect this iteration.
        for (HandlerEntry<?> entry : list) {
            try {
                // The cast is safe: the map key is event.getClass(), and entry was added to the
                // list keyed by the same type. The Consumer<T> accepts T=event.getClass().
                ((Consumer<Object>) (Consumer<?>) entry.handler()).accept(event);
            } catch (Exception ex) {
                // Error isolation: swallow and continue. In production you'd log or route to
                // a dead-letter queue. Stopping here would mean one buggy plugin breaks all
                // other subscribers for the same event type — unacceptable in a plugin system.
                // Intentional suppression — see class Javadoc for rationale.
            }
        }
    }
}
