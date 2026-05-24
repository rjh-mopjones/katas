package org.kata.eventbus;

/**
 * A handle to a single handler registration in an {@link EventBus}.
 *
 * <p>Calling {@link #unsubscribe()} removes the associated handler so it no longer receives
 * events. After unsubscription the handle is inert — calling {@code unsubscribe()} again is a
 * no-op (idempotent by design; callers should not need to track whether they already cancelled).
 *
 * <p><b>Why a separate interface (not a Runnable or AutoCloseable)?</b>
 * <ul>
 *   <li>{@code AutoCloseable} would work and allow try-with-resources, but suggests a resource
 *       that must be closed (like a file or connection). Subscriptions are typically long-lived
 *       and only cancelled on a lifecycle event — the "must close" mental model is wrong.</li>
 *   <li>A named {@code Subscription} type makes the API self-documenting at the call site:
 *       {@code Subscription sub = bus.subscribe(...); sub.unsubscribe();} reads naturally.</li>
 * </ul>
 *
 * <p>Production equivalents: RxJava's {@code Disposable}, Project Reactor's {@code Subscription},
 * and Guava EventBus have no unsubscribe handle — they require the original handler object for
 * unregistration, which is awkward with lambdas. A token-based approach like this is more
 * convenient for callers.
 */
public interface Subscription {
    /** Remove the associated handler from the event bus. Safe to call multiple times. */
    void unsubscribe();
}
