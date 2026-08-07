# Event Bus

## Approach
The bus is a `ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<HandlerEntry<?>>>` — event type mapped
to an ordered list of handlers for that type. `ConcurrentHashMap` gives lock-free concurrent
subscribe/publish across *different* event types (per-bucket locking, no global lock). Within a single
type's handler list, `CopyOnWriteArrayList` is the right structure because publishes (reads) vastly
outnumber subscribe/unsubscribe (writes): every mutation copies the backing array, so an in-flight
`publish` iterates a stable snapshot and never throws `ConcurrentModificationException`, with no lock
needed during delivery.

Each subscription is wrapped in a `HandlerEntry<T>` record pairing the type token with the handler.
Because it's a record, equality is structural — but since lambdas have no value equality, two separate
`subscribe()` calls with the *same* lambda reference still produce two distinct `HandlerEntry` objects.
That's what makes `Subscription.unsubscribe()` remove *exactly* the right registration (via
`CopyOnWriteArrayList.remove(Object)`, which uses `equals()`) even when the same handler is registered
twice — and makes double-unsubscribe naturally idempotent via a `volatile boolean cancelled` guard.

`publish` looks up handlers by `event.getClass()` — the runtime type, not the declared reference type
— so a `Dog` published through an `Animal`-typed variable still dispatches to `Dog.class` handlers
only. Each handler invocation is wrapped in its own try/catch; a handler that throws is swallowed and
the loop continues, so one broken subscriber can never silently starve the rest.

## The real challenge
- **`CopyOnWriteArrayList` for safe concurrent iteration**: `publish` iterates the handler list while `subscribe`/`unsubscribe` may mutate it concurrently. `CopyOnWriteArrayList` takes a snapshot of the backing array at iteration start, so modifications during publish are never visible to that iteration — no lock needed during delivery.
- **Unsubscribe identity**: `subscribe` captures the `HandlerEntry` object in the returned lambda. `CopyOnWriteArrayList.remove(Object)` uses `equals()`; `HandlerEntry` is a record so equality is structural. Lambdas do not implement value equality — two registrations of the same lambda reference produce two distinct `HandlerEntry` objects — so `remove(entry)` removes exactly the right one even when the same handler is subscribed twice.
- **Error isolation**: wrapping each handler invocation in try/catch and continuing is essential. Stopping on the first failure would silently starve all remaining subscribers — the kind of bug that is invisible in tests with only one handler per type.
- **Type dispatch via `getClass()`**: dispatch is on the concrete runtime type, not the declared compile-time type. Handlers for `Animal.class` do not receive a `Dog` event.

## Common mistakes & senior signal
- Guarding the whole bus with a single `synchronized` block or lock — it compiles and passes simple
  tests, but serializes all publishes across all event types, which defeats the point of a
  `ConcurrentHashMap`. The signal is choosing a structure per access pattern (map: concurrent
  read/write across keys; list: read-heavy, snapshot-on-iterate).
- Storing handlers in a plain `ArrayList` and iterating it during `publish` — a concurrent
  subscribe/unsubscribe throws `ConcurrentModificationException`. Recognizing *why* `CopyOnWriteArrayList`
  specifically fixes this (snapshot semantics, not just "it's thread-safe") is the deeper signal.
- Letting an unsubscribe token capture the raw `Consumer` and removing by reference — this quietly
  breaks when the same lambda is subscribed twice, since two subscriptions to the same reference would
  both try to remove "the" registration. Wrapping each subscription in its own identity-bearing object
  (the `HandlerEntry`) avoids that.
- Letting a handler exception propagate out of `publish` — stops delivery to every handler registered
  after the failing one, for every future publish of that type if the failure recurs.

## Extensions
- An asynchronous bus: wrap each handler invocation in `executor.submit(() -> handler.accept(event))`
  so a slow handler doesn't block the publisher or delay other handlers — trades ordering/visibility
  guarantees for throughput.
- Hierarchy-aware dispatch (as Guava's `EventBus` does): walk superclasses and interfaces so a handler
  registered for `Animal.class` also receives `Dog` events — enables polymorphic dispatch at the cost
  of harder-to-predict invocation order and handler sets.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/eventbus/`)
- Java Interview Primer: Q82 (Observer), Q188 (Spring events), Q33 (fail-safe iterators / CopyOnWriteArrayList)
