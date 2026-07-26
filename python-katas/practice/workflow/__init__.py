"""Order state machine — implement `apply`; the enums, events, and exceptions are provided."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum, auto


class OrderState(Enum):
    """The lifecycle states of an order. ``FILLED``/``CANCELLED``/``REJECTED`` are terminal."""

    NEW = auto()
    ACCEPTED = auto()
    PARTIALLY_FILLED = auto()
    FILLED = auto()
    CANCELLED = auto()
    REJECTED = auto()


@dataclass(frozen=True)
class Order:
    """An immutable snapshot of an order: its state, total quantity, and quantity filled so far."""

    state: OrderState
    total: int
    filled: int = 0


@dataclass(frozen=True)
class Accept:
    """The venue accepted the order."""


@dataclass(frozen=True)
class Fill:
    """A (partial or full) fill of ``qty`` units."""

    qty: int


@dataclass(frozen=True)
class Cancel:
    """A request to cancel the order."""


@dataclass(frozen=True)
class Reject:
    """The venue rejected the order."""


Event = Accept | Fill | Cancel | Reject


class IllegalTransition(Exception):
    """Raised when an event is not legal from the order's current state."""


class Overfill(Exception):
    """Raised when a fill would push the filled quantity past the order's total."""


def apply(order: Order, event: Event) -> Order:
    """Apply ``event`` to ``order``, returning the resulting new ``Order``. You design the rules."""
    raise NotImplementedError
