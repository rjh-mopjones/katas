"""Feed parser — a streaming market-data feed reader. Implement `parse_feed` / `parse_all`.

`Quote`, `ErrorKind`, `ParseError`, and `Parsed` are provided; you design the parsing.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass
from enum import Enum, auto


@dataclass(frozen=True)
class Quote:
    """A two-way price for one instrument: ``SYMBOL|BID|ASK|QTY``."""

    symbol: str
    bid: float
    ask: float
    qty: int


class ErrorKind(Enum):
    """Why a physical feed line failed to parse (checked in this order)."""

    WRONG_FIELD_COUNT = auto()  # not exactly 4 pipe-delimited fields
    EMPTY_SYMBOL = auto()  # symbol field blank after trim
    INVALID_BID = auto()  # bid did not parse as a float
    INVALID_ASK = auto()  # ask did not parse as a float
    INVALID_QTY = auto()  # qty not a non-negative int


@dataclass(frozen=True)
class ParseError(Exception):
    """A data-carrying reject: the 1-based physical ``line`` and the ``kind`` of failure."""

    line: int
    kind: ErrorKind


@dataclass(frozen=True)
class Parsed:
    """One non-skipped line's outcome: exactly one of ``quote`` / ``error`` is set."""

    line: int
    quote: Quote | None
    error: ParseError | None


def parse_feed(lines: Iterable[str]) -> Iterator[Parsed]:
    raise NotImplementedError


def parse_all(lines: Iterable[str]) -> tuple[list[Quote], list[ParseError]]:
    raise NotImplementedError
