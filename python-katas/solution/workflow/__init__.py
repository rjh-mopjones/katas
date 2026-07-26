"""Order state machine — guarded transitions for a trading order's lifecycle.

# The scenario

A trading order moves through a fixed lifecycle: it is ``NEW``, then the venue either ``ACCEPTED``
or ``REJECTED`` it; an accepted order fills — possibly in pieces (``PARTIALLY_FILLED``) until it is
fully ``FILLED`` — or is ``CANCELLED`` before completing. ``FILLED``, ``CANCELLED`` and ``REJECTED``
are **terminal**: nothing more can happen to the order. Each incoming market event (an accept, a
fill of some quantity, a cancel, a reject) is only legal from certain states, and a fill must never
push the filled quantity past the order's total.

# The design: a pure transition function over (state, event)

[`apply`] is a **pure function**: it takes the current [`Order`] and an [`Event`] and returns a
*new* frozen ``Order`` (via :func:`dataclasses.replace`) — it never mutates its input. That makes
the state machine trivial to test and reason about: no hidden state, no aliasing surprises, and an
order value can be safely shared or logged. This is the **State pattern** expressed functionally —
behaviour is selected by the current state rather than by a tangle of boolean flags.

The whole rule table is a single ``match (order.state, event)``. Structural pattern matching is the
right tool here: **class patterns** destructure the event (``Fill(qty=q)`` binds the fill size),
**OR-patterns** collapse states that share a rule (``ACCEPTED | PARTIALLY_FILLED`` fill the same
way), and a final ``case _`` catch-all rejects everything else as an [`IllegalTransition`]. Note
the catch-all is *deliberate*: Python's ``match`` does **not** enforce exhaustiveness at compile
time, so the wildcard is what guarantees an unhandled ``(state, event)`` pair fails loudly instead
of silently returning ``None``.

# Why the guards matter (the money angle)

An illegal transition that slips through is not a cosmetic bug. Filling an already-``CANCELLED``
order, or letting a second fill push past ``total`` (a double-fill / [`Overfill`]), books quantity
the desk never intended to trade — a real, unhedged position and real P&L. The guards are the thing
standing between the matching engine and an accidental trade, so they raise rather than clamp.

# Alternative: the typestate pattern

A more radical design makes each state a *distinct type* — ``NewOrder``, ``AcceptedOrder``,
``FilledOrder`` … — where only the legal events are methods on each class (``NewOrder.accept()``
returns an ``AcceptedOrder``; there simply is no ``FilledOrder.fill``). Illegal transitions then
fail to **type-check** and never reach runtime — the compiler/type-checker becomes the guard. The
trade-off: you cannot hold "an order in some state" in one variable without a union and a ``match``
to narrow it, event dispatch from a wire protocol still needs a runtime switch, and the type
explosion is heavier for a six-state machine. The single-function form here is the one to reach for
first; reach for typestate when illegal transitions must be statically impossible.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
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
    """Apply ``event`` to ``order``, returning the resulting new ``Order``.

    Never mutates ``order``; raises [`IllegalTransition`] for a disallowed ``(state, event)`` pair
    and [`Overfill`] when a fill would exceed ``total``.
    """
    match (order.state, event):
        case (OrderState.NEW, Accept()):
            return replace(order, state=OrderState.ACCEPTED)
        case (OrderState.NEW, Reject()):
            return replace(order, state=OrderState.REJECTED)
        case (OrderState.NEW, Cancel()):
            return replace(order, state=OrderState.CANCELLED)

        case (OrderState.ACCEPTED | OrderState.PARTIALLY_FILLED, Fill(qty=q)):
            new_filled = order.filled + q
            if new_filled > order.total:
                raise Overfill(f"fill of {q} would overfill: {order.filled}+{q} > {order.total}")
            if new_filled == order.total:
                return replace(order, state=OrderState.FILLED, filled=order.total)
            return replace(order, state=OrderState.PARTIALLY_FILLED, filled=new_filled)

        case (OrderState.ACCEPTED | OrderState.PARTIALLY_FILLED, Cancel()):
            return replace(order, state=OrderState.CANCELLED)

        case _:
            raise IllegalTransition(f"cannot apply {type(event).__name__} from {order.state.name}")
