/// An event: a `topic` string and an `i64` payload. Provided verbatim.
pub struct Event {
    pub topic: String,
    pub payload: i64,
}

/// A synchronous pub/sub dispatcher. Design the internals (topics → handlers) yourself.
#[derive(Default)]
pub struct EventBus;

impl EventBus {
    pub fn new() -> Self {
        todo!("construct an empty bus")
    }

    pub fn subscribe<F: Fn(&Event) + 'static>(&mut self, _topic: &str, _handler: F) {
        todo!("box the handler and store it under the topic")
    }

    pub fn publish(&self, _event: &Event) {
        todo!("call every handler registered for event.topic, in order")
    }

    pub fn subscriber_count(&self, _topic: &str) -> usize {
        todo!("count handlers registered for the topic")
    }
}
