# Event Bus

> Build a type-keyed synchronous pub/sub bus that stays correct while handlers subscribe, publish, and unsubscribe concurrently.

## The problem
Implement an in-process event bus where subscribers register interest in a specific event type and receive all future events of that exact runtime type. Publishers fire an event without knowing who is listening. The bus must deliver to all registered handlers, isolate handler exceptions so one failure cannot silently block others, and return a cancellable `Subscription` token.

## Requirements
- `subscribe(Class<T> type, Consumer<T> handler)` registers the handler and returns a `Subscription`.
- `Subscription.unsubscribe()` removes exactly that registration; calling it multiple times is a no-op.
- `publish(Object event)` dispatches to all handlers registered for `event.getClass()` (exact runtime type — not superclasses or interfaces). If no handlers are registered, publish is a silent no-op.
- Handlers are invoked in registration order (FIFO).
- If a handler throws, the exception is suppressed and the remaining handlers still receive the event.
- The bus must be safe under concurrent publish calls and concurrent subscribe/unsubscribe during publish — no `ConcurrentModificationException` and no missed or duplicate deliveries.
- Null `type`, `handler`, and `event` arguments are rejected.

## What you implement
Implement `EventBus` from scratch — the public API is `subscribe(Class<T>, Consumer<T>)` (returns a `Subscription`) and `publish(Object)`. You design the internal handler registry, unsubscribe mechanism, and concurrent iteration strategy yourself. The `Subscription` interface (with its single `unsubscribe()` method) is provided as a working type.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/eventbus/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
