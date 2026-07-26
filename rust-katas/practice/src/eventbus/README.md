# Event Bus

> Wire independent components together without hard references — publishers announce events on a topic, subscribers react, and neither knows the other exists (the observer pattern).

## The problem

Build a synchronous publish/subscribe `EventBus`. A subscriber registers a handler for a *topic*;
when an `Event` is published to that topic, every handler registered for it runs — in the order they
subscribed. Publishing to a topic nobody listens on does nothing. Handlers of wildly different shapes
(a logger, a counter, an accumulator) must be storable side by side under the same topic.

## Requirements

- `subscribe(topic, handler)` stores `handler` under `topic`. Multiple handlers per topic are allowed
  and keep subscription order.
- `publish(event)` calls every handler registered for `event.topic`, in order, passing `&event`.
- Publishing to a topic with no subscribers is a no-op (no panic).
- `subscriber_count(topic)` returns how many handlers are registered for that topic (0 if none).
- Handlers are `Fn(&Event)` — callable through a shared reference, any number of times.

## What you implement

- `fn new() -> Self` and `impl Default`
- `fn subscribe<F: Fn(&Event) + 'static>(&mut self, topic: &str, handler: F)`
- `fn publish(&self, event: &Event)`
- `fn subscriber_count(&self, topic: &str) -> usize`

`Event { topic: String, payload: i64 }` is provided verbatim. You design `EventBus`'s internals and
write the four methods.

## The real challenge

- **`Box<dyn Fn(&Event)>` trait objects.** Each closure has its own anonymous type, but they must
  share one list per topic. A `Vec<F>` for a generic `F` is homogeneous — it can't hold two different
  closures. Box each into `Box<dyn Fn(&Event)>` (a fat pointer: data + vtable) so heterogeneous
  handlers live together in `Vec<Box<dyn Fn(&Event)>>`.
- **`dyn` vs generics — dynamic vs static dispatch.** A generic `subscribe<F: Fn>` is monomorphised
  and inlined (static dispatch, no indirection) but pins storage to one concrete `F`. `dyn Fn`
  dispatches through the vtable (an indirect call + a heap allocation per handler) but accepts a mixed
  bag. The idiom here: stay generic at the *call site*, erase to `dyn` in *storage* — `subscribe<F>`
  boxes `F` internally.
- **Closures as stored values.** A closure that captures state is a value you can move into a `Box`
  and keep. `+ 'static` says the captured data outlives the bus.
- **`Fn` vs `FnMut`/`FnOnce`.** `publish(&self)` calls handlers repeatedly through a shared reference
  → `Fn`. `FnMut` would force `publish(&mut self)`; `FnOnce` could fire once. Because handlers can't
  mutate their captures directly, observe side effects in your tests via `Rc<RefCell<T>>` /
  `Rc<Cell<T>>`.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` in this
file (capture shared state with `Rc<RefCell<T>>` or `Rc<Cell<T>>` to observe that handlers fired),
then:

```
cd rust-katas && cargo test -p practice eventbus
```

## Reference

Worked solution: `rust-katas/solution/src/eventbus/`.

Extension: return a subscription *handle* from `subscribe` that unsubscribes the handler when it is
dropped (RAII); or make the bus generic over the event type — `EventBus<E>` with
`Vec<Box<dyn Fn(&E)>>` — so one bus type serves any payload.

Background: [The Rust Book — Using Trait Objects That Allow for Values of Different Types](https://doc.rust-lang.org/book/ch18-02-trait-objects.html).
