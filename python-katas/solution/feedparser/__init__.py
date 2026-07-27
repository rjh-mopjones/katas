"""Feed parser — a streaming market-data feed reader.

# The scenario

A market-data gateway receives quotes as a text feed: one pipe-delimited record per line,
``SYMBOL|BID|ASK|QTY`` — e.g. ``LIV-MUN|1.95|2.05|1000`` for a two-way price on a match's
outright. The feed is a firehose that arrives over a socket, a file tail, or a message replay; it
is annotated with ``#`` comment lines and blank separators, and — because it comes off a wire from a
dozen venues — some lines are malformed: a missing field, an empty symbol, a price that won't parse,
a negative size. A feed handler must turn this text stream into typed [`Quote`]s **as they arrive**,
and it must never throw the whole batch away because one line was bad: every reject is reported with
the exact physical line number so an operator can find it in the raw capture.

# The design: a lazy generator over any line iterable

[`parse_feed`] is a **generator**, not a function that returns a list. That is the point. The input
is an ``Iterable[str]`` — a file object, a socket-line iterator, a ``str.splitlines()`` list,
anything you can loop over — and the parser pulls one line at a time and ``yield``s one [`Parsed`]
per non-skipped line. It never materialises the whole feed: memory is O(1) in the number of lines,
so it handles an unbounded stream, and a consumer that only wants the first few quotes (``islice``,
an early ``break``) stops the parser mid-feed without reading the rest. It composes with ``for``,
``itertools``, and a *take*, exactly like a Unix pipe.

Each yielded [`Parsed`] carries the 1-based **physical** line number and *exactly one* of ``quote``
/ ``error`` — a success or a data-carrying [`ParseError`]. Modelling the reject as a value (a frozen
dataclass that also subclasses ``Exception``) rather than raising keeps the stream flowing: one bad
venue line doesn't abort the other 999 good ones, and the caller decides whether to log, alert, or
halt. [`parse_all`] is the eager collector on top — it drains the generator into ``(quotes,
errors)`` for tests and batch jobs.

# The rules (the contract every language mirror shares)

After trimming, a **blank** line or one starting with ``#`` is *skipped* — not a record and not an
error — but it still advances the physical line counter, so error line numbers point at the real
line in the capture. A record must have exactly four ``|``-fields; validation runs in a fixed order
so the first problem wins deterministically: field-count → empty-symbol → bid → ask → qty.
``bid``/``ask`` are floats; ``qty`` is a **non-negative** integer (a negative size is nonsense).

# Why it matters (the money angle)

This is the ingest edge of a pricing stack: every downstream book, hedge, and fill is priced off
these quotes. Parsing lazily lets the gateway keep up with a live feed at low latency instead of
batching to end-of-file; reporting per-line errors instead of throwing means one venue's malformed
print never blacks out the rest of the market; and rejecting a bad price at the door — a non-numeric
bid, a negative size — stops a garbage quote from ever reaching the book, where it would misprice or
crash a downstream strategy.
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
    """A data-carrying reject: the 1-based physical ``line`` and the ``kind`` of failure.

    Subclasses ``Exception`` so a caller *may* raise it, but the parser treats it as a **value** and
    ``yield``s it inside a [`Parsed`] rather than raising — one bad line must not abort the stream.
    """

    line: int
    kind: ErrorKind


@dataclass(frozen=True)
class Parsed:
    """One non-skipped line's outcome: exactly one of ``quote`` / ``error`` is set."""

    line: int
    quote: Quote | None
    error: ParseError | None


def parse_feed(lines: Iterable[str]) -> Iterator[Parsed]:
    """Parse a feed of ``SYMBOL|BID|ASK|QTY`` lines into [`Parsed`] items, lazily.

    Consumes ``lines`` one at a time and ``yield``s one [`Parsed`] per non-skipped line — never
    builds a list of the input, so it streams an unbounded feed in O(1) memory and stops the instant
    the consumer does. Blank lines and ``#`` comments (after trimming) are skipped but still advance
    the 1-based physical line number, so every [`ParseError`] points at the real captured line.

    Validation is order-sensitive so the first problem wins: field-count → empty-symbol → bid → ask
    → qty. ``bid``/``ask`` parse as floats; ``qty`` must be a non-negative int.
    """
    for line_no, raw in enumerate(lines, start=1):
        text = raw.strip()
        if not text or text.startswith("#"):
            continue

        fields = text.split("|")
        if len(fields) != 4:
            yield _error(line_no, ErrorKind.WRONG_FIELD_COUNT)
            continue

        symbol, bid_s, ask_s, qty_s = fields
        symbol = symbol.strip()
        if not symbol:
            yield _error(line_no, ErrorKind.EMPTY_SYMBOL)
            continue

        try:
            bid = float(bid_s)
        except ValueError:
            yield _error(line_no, ErrorKind.INVALID_BID)
            continue

        try:
            ask = float(ask_s)
        except ValueError:
            yield _error(line_no, ErrorKind.INVALID_ASK)
            continue

        qty = _parse_qty(qty_s)
        if qty is None:
            yield _error(line_no, ErrorKind.INVALID_QTY)
            continue

        yield Parsed(line_no, Quote(symbol, bid, ask, qty), None)


def parse_all(lines: Iterable[str]) -> tuple[list[Quote], list[ParseError]]:
    """Drain [`parse_feed`] into ``(quotes, errors)`` — the eager collector for tests and jobs."""
    quotes: list[Quote] = []
    errors: list[ParseError] = []
    for item in parse_feed(lines):
        if item.quote is not None:
            quotes.append(item.quote)
        else:
            assert item.error is not None
            errors.append(item.error)
    return quotes, errors


def _error(line_no: int, kind: ErrorKind) -> Parsed:
    """Wrap a reject as a [`Parsed`] carrying a [`ParseError`] (never raised — yielded as value)."""
    return Parsed(line_no, None, ParseError(line_no, kind))


def _parse_qty(text: str) -> int | None:
    """Parse a non-negative integer size, or ``None`` if it isn't one (float/negative/garbage)."""
    try:
        qty = int(text)
    except ValueError:
        return None
    return qty if qty >= 0 else None
