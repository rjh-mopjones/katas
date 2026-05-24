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

## The real challenge
- **`CopyOnWriteArrayList` for safe concurrent iteration**: `publish` iterates the handler list while `subscribe`/`unsubscribe` may mutate it concurrently. `CopyOnWriteArrayList` takes a snapshot of the backing array at iteration start, so modifications during publish are never visible to that iteration — no lock needed during delivery.
- **Unsubscribe identity**: `subscribe` captures the `HandlerEntry` object in the returned lambda. `CopyOnWriteArrayList.remove(Object)` uses `equals()`; `HandlerEntry` is a record so equality is structural. Lambdas do not implement value equality — two registrations of the same lambda reference produce two distinct `HandlerEntry` objects — so `remove(entry)` removes exactly the right one even when the same handler is subscribed twice.
- **Error isolation**: wrapping each handler invocation in try/catch and continuing is essential. Stopping on the first failure would silently starve all remaining subscribers — the kind of bug that is invisible in tests with only one handler per type.
- **Type dispatch via `getClass()`**: dispatch is on the concrete runtime type, not the declared compile-time type. Handlers for `Animal.class` do not receive a `Dog` event.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/eventbus/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/eventbus/`
- Java Interview Primer: Q82 (Observer), Q188 (Spring events), Q33 (fail-safe iterators / CopyOnWriteArrayList)
