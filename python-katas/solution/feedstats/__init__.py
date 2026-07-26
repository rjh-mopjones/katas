"""Feed stats — a live market-data stream aggregator.

# The scenario

A market-data feed emits an unbounded stream of trade **ticks** — one per print on the tape, each a
``(timestamp, price, quantity)``. Traders never want the raw firehose; they want it rolled up into
fixed-period **bars**: the open/high/low/close and volume-weighted average price (VWAP) over each
one-minute (or five-second, or hourly) window. A charting UI, a signal, a risk check — all of them
consume *bars*, not ticks. The feed runs all session, so the aggregator must turn ticks into bars
**as they stream**, holding only the bar it is currently building — never the whole session in RAM.

# The design: a lazy one-tick-look-ahead state machine

[`bars`] is a **generator**, not a function that returns a list. That is the whole point. A list
would force us to read the entire feed before returning the first bar — impossible on an unbounded
stream, and wasteful even on a finite one (the whole session sits in memory). A generator is
pull-based and lazy: it computes and ``yield``s each completed bar the instant it is known, then
suspends until the consumer asks for the next. It composes with ``for``, ``itertools``, and an
``islice``-style *take* for free, and its memory is O(1) in the number of ticks — only the
in-progress bar's running aggregates are held.

The subtlety is knowing when a bar is *done*. A bar for window ``W`` is complete only once we see a
tick belonging to a **later** window — that tick is both the signal to flush ``W`` and the first
tick of the next bar. So the generator is a small state machine with **one tick of look-ahead**: it
accumulates ticks into the current window's running open/high/low/close, volume, and the two VWAP
sums (``Σ price·qty`` and ``Σ qty``); when a tick crosses into a new window it ``yield``s the
finished bar, then re-seeds the accumulator with the tick that triggered the flush. When the stream
ends it ``yield``s the final, partial bar. No look-ahead buffer, no list of ticks — just the running
totals plus the one boundary tick that opened the next window.

# Why it matters (the money angle)

VWAP is an execution benchmark: fills are judged good or bad against it, and it feeds pricing and
TWAP/VWAP order slicers. Computing it in a single streaming pass keeps the aggregator honest on a
live feed — you can bar up an all-day tape without ever holding the session in RAM, and you emit
each bar with minimum latency instead of at end-of-day. Get the windowing or the weighting wrong and
every downstream bar is mispriced: a bad VWAP makes good fills look like slippage and mis-marks the
book.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass


@dataclass(frozen=True)
class Tick:
    """A single trade print off the tape."""

    ts: int  # seconds
    price: float
    qty: int


@dataclass(frozen=True)
class Bar:
    """One period's OHLC + volume + VWAP aggregate."""

    start: int  # window start = (ts // period) * period
    open: float
    high: float
    low: float
    close: float
    volume: int
    vwap: float  # sum(price*qty) / sum(qty) over the window


def bars(ticks: Iterable[Tick], period: int) -> Iterator[Bar]:
    """Aggregate a time-ordered stream of ``ticks`` into fixed-``period`` OHLC+VWAP bars, lazily.

    Yields each completed bar as soon as a tick from a later window arrives, then flushes the final
    (possibly partial) bar when the stream ends. Holds only the in-progress bar's running totals in
    memory — O(1) in the number of ticks, so it aggregates an unbounded feed without buffering it.

    ``period`` is the window length in seconds; each tick lands in the window starting at
    ``(ts // period) * period``. Raises ``ValueError`` if ``period <= 0``. Empty input yields none.
    """
    if period <= 0:
        raise ValueError(f"period must be positive, got {period}")

    start: int | None = None
    open_: float = 0.0
    high: float = 0.0
    low: float = 0.0
    close: float = 0.0
    volume: int = 0
    weighted: float = 0.0  # Σ price·qty over the window

    for tick in ticks:
        window = (tick.ts // period) * period
        if start is None:
            start = window
            open_ = high = low = close = tick.price
            volume = tick.qty
            weighted = tick.price * tick.qty
            continue
        if window != start:
            # The boundary tick closes the current bar and opens the next one.
            yield Bar(start, open_, high, low, close, volume, weighted / volume)
            start = window
            open_ = high = low = close = tick.price
            volume = tick.qty
            weighted = tick.price * tick.qty
            continue
        high = max(high, tick.price)
        low = min(low, tick.price)
        close = tick.price
        volume += tick.qty
        weighted += tick.price * tick.qty

    if start is not None:
        yield Bar(start, open_, high, low, close, volume, weighted / volume)
