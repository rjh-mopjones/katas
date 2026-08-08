# Event Bus

> Build a type-keyed synchronous pub/sub bus that stays correct while handlers subscribe, publish, and unsubscribe concurrently.

## The problem
Implement an in-process event bus where subscribers register interest in a specific event type and receive all future events of that exact runtime type. Publishers fire an event without knowing who is listening. The bus must deliver to all registered handlers, isolate handler exceptions so one failure cannot silently block others, and hand back a cancellable subscription token.

## Requirements
- Subscribing registers a handler for a given event type and yields a `Subscription` token the caller can later cancel.
- Cancelling a subscription removes exactly that registration; cancelling again is a no-op.
- Publishing an event dispatches it to every handler registered for the event's exact runtime type (not superclasses or interfaces). If no handlers are registered, publishing is a silent no-op.
- Handlers are invoked in registration order (FIFO).
- If a handler throws, the exception is suppressed and the remaining handlers still receive the event.
- The bus must be safe under concurrent publishes and concurrent subscribe/unsubscribe during a publish — no `ConcurrentModificationException` and no missed or duplicate deliveries.
- Null type, handler, and event arguments are rejected.

## What you're given
- `Subscription` — an interface with a single `unsubscribe()` method, the cancellable token your bus hands back on subscribe.

You design the entire public API — method names, parameters, return types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/eventbus/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
