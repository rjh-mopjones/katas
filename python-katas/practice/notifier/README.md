# Notification Service

> A pub/sub notification service — subscribers register for topics over pluggable channels (email/SMS/push), and publishing fans an event out to everyone listening.

## The problem

A backend needs to tell users when things happen: an order ships, a ride arrives, a price alert
fires. Different users want different transports and only for the **topics** they care about —
sometimes only for events matching a filter ("orders over $100"). Publishing an event must fan it
out to every interested transport, in a predictable order, without the publisher knowing who is
listening.

Build the service at the centre of that: subscribers register a channel for a topic (optionally with
a filter), and publishing delivers to the matching channels.

## Requirements

- `subscribe(topic, channel)` registers a channel for a topic. A channel is **any object with a
  `send(event)` method** — no base class to inherit.
- `subscribe(topic, channel, predicate=...)` registers with a filter: `predicate(event) -> bool`.
- `publish(event)` delivers the event to every channel subscribed to `event.topic` whose predicate
  passes (or that has none), **in subscription order**, by calling `channel.send(event)`.
- A channel only receives events for its own topic; publishing to another topic doesn't call it.
- Publishing to a topic with no subscribers is a no-op.
- `subscriber_count(topic)` returns how many channels are subscribed to that topic.

## What you implement

- `NotificationService.subscribe(topic, channel, *, predicate=None) -> None`
- `NotificationService.publish(event) -> None`
- `NotificationService.subscriber_count(topic) -> int`

`Event` (a frozen dataclass) and the `Channel` protocol are provided. You design the storage.

## The real challenge

- **`Channel` is a Protocol, not a base class.** Model the transport with `typing.Protocol` —
  *structural / duck typing*. Any object with a matching `send(event)` conforms without ever naming
  or inheriting `Channel`, so third-party transports slot in for free. Dispatch to
  `channel.send(event)` is ordinary dynamic dispatch.
- **This is the Observer pattern.** The service is the *subject* that holds a list of subscribers
  per topic and pushes each published event to them; publishers stay decoupled from receivers, which
  can register at runtime.
- **Predicates are plain `Callable`s.** A filter is a first-class function `Callable[[Event], bool]`,
  not a subclass overriding a hook — a lambda can express it inline at the subscription site.
- **`@runtime_checkable`** on the Protocol lets `isinstance(obj, Channel)` work — it checks the method
  *exists* (not its signature), which is exactly what a duck-typed registry wants.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_notifier.py` in this
directory (cover order, per-topic isolation, predicate filtering, and the empty-topic no-op), then:

```
cd python-katas && .venv/bin/pytest practice/notifier
```
Compare against the reference: `.venv/bin/pytest solution/notifier`.

## Reference

Worked solution: `solution/notifier/`.

Extension: return an **unsubscribe handle** from `subscribe`; deliver **asynchronously**
(`async def send`, fan out with `asyncio.gather`); add **per-channel retry** on a failing `send`.

Background: [`typing.Protocol` — structural subtyping](https://docs.python.org/3/library/typing.html#typing.Protocol).
