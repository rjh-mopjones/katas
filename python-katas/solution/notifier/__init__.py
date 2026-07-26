"""Notification service — pub/sub fan-out over pluggable channels.

# The scenario

A backend needs to notify users when things happen: an order ships, a ride arrives, a price alert
fires. Different users want different transports — email, SMS, push — and only for the *topics* they
care about, sometimes only for events matching a filter ("orders over $100"). Publishing an event
must fan it out to every interested transport without the publisher knowing who is listening.

# The design: the Observer pattern

This is the **Observer pattern**. ``NotificationService`` is the *subject*: subscribers register
interest in a topic and it holds them in a list. ``publish`` walks the subscribers for the event's
topic and pushes the event to each — the publisher is decoupled from the receivers, which can come
and go at runtime. Delivery is in subscription order so behaviour is deterministic.

# The idiom: ``Channel`` is a Protocol (structural typing)

A channel is *anything with a ``send(event)`` method*. Rather than force every transport to inherit
a base class, [`Channel`] is a [`typing.Protocol`] — **structural / duck typing**. A plain
``class EmailChannel`` with a matching ``send`` *is* a ``Channel`` to the type checker without ever
naming it; there is no inheritance to wire up. Marking it [`@runtime_checkable`] additionally lets
``isinstance(obj, Channel)`` work at runtime (it checks only that the method *exists*, not its
signature). The dispatch to ``channel.send(event)`` is ordinary **dynamic dispatch** — Python looks
the method up on the object at call time, so any conforming object slots in.

Filters are plain [`Callable`]s: ``predicate(event) -> bool``. Passing a function as a first-class
value (rather than subclassing to override a ``matches`` hook) keeps subscriptions declarative and
lets a lambda express the filter inline.

# Alternatives

Nominal typing via an ABC base class (``class Channel(ABC)`` with an abstract ``send``) would force
every transport to inherit — more coupling, and third-party objects you don't control can't
conform. An ``enum`` of fixed channel *types* with a big ``match`` in ``publish`` centralises
dispatch but closes the set: adding a transport means editing the service. The Protocol keeps the
set of channels open and the service oblivious to concrete types — reach for it first.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable


@dataclass(frozen=True)
class Event:
    """An immutable notification: a ``topic`` string and an arbitrary ``payload`` dict."""

    topic: str
    payload: dict = field(default_factory=dict)


@runtime_checkable
class Channel(Protocol):
    """Structural type for a transport: any object with ``send(event) -> None`` qualifies."""

    def send(self, event: Event) -> None: ...


@dataclass(frozen=True)
class _Subscription:
    channel: Channel
    predicate: Callable[[Event], bool] | None


class NotificationService:
    """The Observer *subject*: fans published events out to subscribed channels by topic."""

    def __init__(self) -> None:
        self._subscribers: dict[str, list[_Subscription]] = {}

    def subscribe(
        self,
        topic: str,
        channel: Channel,
        *,
        predicate: Callable[[Event], bool] | None = None,
    ) -> None:
        """Register ``channel`` for ``topic``, optionally filtered by ``predicate``."""
        self._subscribers.setdefault(topic, []).append(_Subscription(channel, predicate))

    def publish(self, event: Event) -> None:
        """Deliver ``event`` to every channel on ``event.topic`` whose predicate passes."""
        for sub in self._subscribers.get(event.topic, ()):
            if sub.predicate is None or sub.predicate(event):
                sub.channel.send(event)

    def subscriber_count(self, topic: str) -> int:
        """Return how many channels are subscribed to ``topic``."""
        return len(self._subscribers.get(topic, ()))
