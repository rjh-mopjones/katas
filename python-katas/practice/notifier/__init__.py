"""Notification service — pub/sub fan-out over pluggable channels.

Implement `NotificationService`; `Event` and the `Channel` protocol are provided.
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


class NotificationService:
    """The Observer *subject*: fans published events out to subscribed channels by topic.

    You design the internals.
    """

    def __init__(self) -> None:
        raise NotImplementedError

    def subscribe(
        self,
        topic: str,
        channel: Channel,
        *,
        predicate: Callable[[Event], bool] | None = None,
    ) -> None:
        raise NotImplementedError

    def publish(self, event: Event) -> None:
        raise NotImplementedError

    def subscriber_count(self, topic: str) -> int:
        raise NotImplementedError
