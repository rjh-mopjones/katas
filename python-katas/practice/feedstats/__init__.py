"""Feed stats — a live market-data aggregator. Implement `bars`; `Tick`/`Bar` are provided."""

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
    raise NotImplementedError
