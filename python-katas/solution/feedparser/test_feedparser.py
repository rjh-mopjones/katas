from collections.abc import Iterator

from . import ErrorKind, Parsed, ParseError, Quote, parse_all, parse_feed

# The canonical sample feed — shared verbatim across all six language mirrors.
CANONICAL = [
    "# market data feed",
    "LIV-MUN|1.95|2.05|1000",
    "",
    "ARS-CHE|1.50|1.60|500",
    "|1.0|2.0|10",
    "BAD|x|2.0|10",
    "TOO|1.0|2.0",
    "NEG|1.0|2.0|-5",
]


def test_canonical_feed_quotes_are_exact():
    quotes, _ = parse_all(CANONICAL)
    assert quotes == [
        Quote("LIV-MUN", 1.95, 2.05, 1000),
        Quote("ARS-CHE", 1.50, 1.60, 500),
    ]


def test_canonical_feed_errors_are_exact():
    _, errors = parse_all(CANONICAL)
    assert [(e.line, e.kind) for e in errors] == [
        (5, ErrorKind.EMPTY_SYMBOL),
        (6, ErrorKind.INVALID_BID),
        (7, ErrorKind.WRONG_FIELD_COUNT),
        (8, ErrorKind.INVALID_QTY),
    ]


def test_error_carries_physical_line_number():
    # Comments/blanks are skipped but still advance the physical line count.
    _, errors = parse_all(["#c", "", "|1|2|3"])
    assert errors == [ParseError(3, ErrorKind.EMPTY_SYMBOL)]


def test_wrong_field_count_too_few_and_too_many():
    _, errors = parse_all(["A|1.0|2.0", "A|1.0|2.0|3|4"])
    assert [(e.line, e.kind) for e in errors] == [
        (1, ErrorKind.WRONG_FIELD_COUNT),
        (2, ErrorKind.WRONG_FIELD_COUNT),
    ]


def test_empty_symbol_variant():
    _, errors = parse_all(["   |1.0|2.0|3"])
    assert errors == [ParseError(1, ErrorKind.EMPTY_SYMBOL)]


def test_invalid_bid_variant():
    _, errors = parse_all(["A|nope|2.0|3"])
    assert errors == [ParseError(1, ErrorKind.INVALID_BID)]


def test_invalid_ask_variant():
    _, errors = parse_all(["A|1.0|nope|3"])
    assert errors == [ParseError(1, ErrorKind.INVALID_ASK)]


def test_invalid_qty_non_integer_and_negative():
    _, errors = parse_all(["A|1.0|2.0|1.5", "A|1.0|2.0|-1", "A|1.0|2.0|nope"])
    assert [e.kind for e in errors] == [
        ErrorKind.INVALID_QTY,
        ErrorKind.INVALID_QTY,
        ErrorKind.INVALID_QTY,
    ]


def test_zero_qty_is_valid():
    quotes, errors = parse_all(["A|1.0|2.0|0"])
    assert quotes == [Quote("A", 1.0, 2.0, 0)]
    assert errors == []


def test_validation_order_first_problem_wins():
    # Wrong field count is checked before empty symbol before bad bid.
    _, errors = parse_all(["|x|2.0"])  # 3 fields -> field count wins over empty symbol/bad bid
    assert errors == [ParseError(1, ErrorKind.WRONG_FIELD_COUNT)]
    _, errors = parse_all(["|x|2.0|3"])  # empty symbol wins over bad bid
    assert errors == [ParseError(1, ErrorKind.EMPTY_SYMBOL)]


def test_empty_input_yields_nothing():
    quotes, errors = parse_all([])
    assert quotes == [] and errors == []
    assert list(parse_feed([])) == []


def test_comment_and_blank_only_feed_yields_nothing():
    assert list(parse_feed(["# header", "   ", "", "# footer"])) == []


def test_parse_feed_yields_one_parsed_per_non_skipped_line():
    items = list(parse_feed(["# c", "A|1.0|2.0|3", "", "B|bad|2.0|3"]))
    assert len(items) == 2
    assert items[0] == Parsed(2, Quote("A", 1.0, 2.0, 3), None)
    assert items[1].line == 4 and items[1].quote is None
    assert items[1].error == ParseError(4, ErrorKind.INVALID_BID)


def test_is_lazy_consumes_incrementally():
    def stream() -> Iterator[str]:
        yield "A|1.0|2.0|1"
        yield "B|3.0|4.0|2"
        raise AssertionError("generator was fully consumed — parsing is not lazy")

    gen = parse_feed(stream())
    first = next(gen)
    assert first.quote == Quote("A", 1.0, 2.0, 1)
    second = next(gen)
    assert second.quote == Quote("B", 3.0, 4.0, 2)
    # We never asked for the third item, so the AssertionError line is never reached.
