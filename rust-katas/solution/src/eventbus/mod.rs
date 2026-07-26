//! Event Bus — a synchronous publish/subscribe dispatcher (the observer pattern).
//!
//! # The component
//!
//! An [`EventBus`] lets many independent handlers register interest in a *topic* and be invoked when
//! an [`Event`] is published to it. `subscribe` stores a handler; `publish` looks up every handler
//! registered for `event.topic` and calls each one, in subscription order. Unknown topic → no-op.
//! It is the classic decoupling primitive: publishers don't know who is listening, subscribers don't
//! know who is sending.
//!
//! # Why `Box<dyn Fn(&Event)>` is the whole point
//!
//! Every subscriber is a different closure — different captured variables, therefore a different
//! anonymous type. Yet they must live together in one `Vec` per topic. You cannot write
//! `Vec<F>` for a generic `F: Fn` and push two *different* closures into it: monomorphisation gives
//! each closure its own concrete type, and a `Vec<F>` is homogeneous. The fix is **type erasure**:
//! box each closure as `Box<dyn Fn(&Event)>`. `dyn Fn` is a *trait object* — a fat pointer of
//! (data pointer, vtable pointer) — so heterogeneous handlers share one `Vec<Box<dyn Fn(&Event)>>`
//! and `publish` calls them through the vtable (dynamic dispatch).
//!
//! The trade is real: a generic `subscribe<F: Fn>` monomorphises and inlines the call (static
//! dispatch, zero indirection) but can only ever hold *one* concrete `F`; `dyn Fn` accepts a mixed
//! bag at the cost of a heap allocation per handler and an indirect call through the vtable. For a
//! bus whose whole job is storing arbitrary listeners together, dynamic dispatch is the *enabling*
//! choice, not a compromise. Note the boundary: `subscribe<F: Fn + 'static>` is generic (static) at
//! the call site, and we box `F` into `dyn Fn` *inside* — generics at the edge, erasure in storage.
//!
//! # Why `Fn`, not `FnMut`/`FnOnce`
//!
//! `publish` takes `&self` and may call a handler any number of times, so a handler must be callable
//! through a shared reference and repeatedly: that is exactly `Fn`. `FnMut` would need `&mut` to the
//! boxed closure (forcing `publish` to take `&mut self` and serialising all publishes); `FnOnce`
//! consumes itself and could fire only once. Choosing `Fn` keeps `publish(&self)` and lets a handler
//! run on every matching event. Handlers therefore can't mutate their captures directly — to observe
//! a side effect, capture shared interior-mutable state (`Rc<RefCell<T>>`, `Rc<Cell<T>>`).
//!
//! # Alternatives
//!
//! Instead of closures you could define a `trait Subscriber { fn on_event(&self, e: &Event); }` and
//! store `Box<dyn Subscriber>` — same trait-object machinery, but named types you can also give other
//! methods (e.g. an id for unsubscribe). Or, if the set of handler kinds is closed and known at
//! compile time, an `enum Handler { Logger, Counter(..), .. }` dispatched by `match` keeps everything
//! static (no allocation, no vtable) at the cost of no longer accepting arbitrary user closures.

use std::collections::HashMap;

/// An event: a `topic` string and an `i64` payload. Delivered by reference to every handler.
pub struct Event {
    pub topic: String,
    pub payload: i64,
}

/// A type-erased event handler: any closure that reads an `&Event`, boxed so handlers of different
/// concrete types share one list.
type Handler = Box<dyn Fn(&Event)>;

/// A synchronous pub/sub dispatcher: topics → ordered lists of type-erased handlers.
#[derive(Default)]
pub struct EventBus {
    handlers: HashMap<String, Vec<Handler>>,
}

impl EventBus {
    /// An empty bus with no subscribers.
    pub fn new() -> Self {
        EventBus {
            handlers: HashMap::new(),
        }
    }

    /// Register `handler` for `topic`. The generic `F` is boxed into a `Box<dyn Fn(&Event)>` so
    /// closures of different concrete types can share one list. Handlers keep insertion order.
    pub fn subscribe<F: Fn(&Event) + 'static>(&mut self, topic: &str, handler: F) {
        self.handlers
            .entry(topic.to_string())
            .or_default()
            .push(Box::new(handler));
    }

    /// Dispatch `event` to every handler registered for `event.topic`, in subscription order.
    /// A topic with no subscribers is a no-op.
    pub fn publish(&self, event: &Event) {
        if let Some(list) = self.handlers.get(&event.topic) {
            for handler in list {
                handler(event);
            }
        }
    }

    /// How many handlers are registered for `topic`.
    pub fn subscriber_count(&self, topic: &str) -> usize {
        self.handlers.get(topic).map_or(0, Vec::len)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::{Cell, RefCell};
    use std::rc::Rc;

    #[test]
    fn subscribe_then_publish_invokes_the_handler() {
        let seen = Rc::new(RefCell::new(Vec::new()));
        let mut bus = EventBus::new();
        let sink = Rc::clone(&seen);
        bus.subscribe("prices", move |e| sink.borrow_mut().push(e.payload));

        bus.publish(&Event {
            topic: "prices".to_string(),
            payload: 42,
        });

        assert_eq!(*seen.borrow(), vec![42]);
    }

    #[test]
    fn multiple_handlers_on_one_topic_all_fire_in_order() {
        let order = Rc::new(RefCell::new(Vec::new()));
        let mut bus = EventBus::new();

        for id in 0..3 {
            let sink = Rc::clone(&order);
            bus.subscribe("t", move |_e| sink.borrow_mut().push(id));
        }

        bus.publish(&Event {
            topic: "t".to_string(),
            payload: 0,
        });

        // Fired in subscription order.
        assert_eq!(*order.borrow(), vec![0, 1, 2]);
    }

    #[test]
    fn a_handler_only_fires_for_its_topic() {
        let hits = Rc::new(Cell::new(0u32));
        let mut bus = EventBus::new();
        let counter = Rc::clone(&hits);
        bus.subscribe("wanted", move |_e| counter.set(counter.get() + 1));

        // Publishing to a different topic must not call it.
        bus.publish(&Event {
            topic: "other".to_string(),
            payload: 0,
        });
        assert_eq!(hits.get(), 0);

        bus.publish(&Event {
            topic: "wanted".to_string(),
            payload: 0,
        });
        assert_eq!(hits.get(), 1);
    }

    #[test]
    fn subscriber_count_reflects_registrations() {
        let mut bus = EventBus::new();
        assert_eq!(bus.subscriber_count("t"), 0);

        bus.subscribe("t", |_e| {});
        bus.subscribe("t", |_e| {});
        bus.subscribe("u", |_e| {});

        assert_eq!(bus.subscriber_count("t"), 2);
        assert_eq!(bus.subscriber_count("u"), 1);
        assert_eq!(bus.subscriber_count("absent"), 0);
    }

    #[test]
    fn publishing_to_a_topic_with_no_subscribers_is_a_no_op() {
        let bus = EventBus::new();
        // Must not panic.
        bus.publish(&Event {
            topic: "nobody-home".to_string(),
            payload: 7,
        });
    }

    #[test]
    fn handler_reads_the_event_payload() {
        let total = Rc::new(Cell::new(0i64));
        let mut bus = EventBus::new();
        let acc = Rc::clone(&total);
        bus.subscribe("sum", move |e| acc.set(acc.get() + e.payload));

        for p in [10, 20, 30] {
            bus.publish(&Event {
                topic: "sum".to_string(),
                payload: p,
            });
        }

        assert_eq!(total.get(), 60);
    }

    #[test]
    fn default_bus_matches_new() {
        let bus = EventBus::default();
        assert_eq!(bus.subscriber_count("anything"), 0);
    }
}
