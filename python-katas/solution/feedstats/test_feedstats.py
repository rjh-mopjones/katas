from collections.abc import Iterator

import pytest

from . import Bar, Tick, bars


def test_single_window_ohlc_and_vwap():
    ticks = [Tick(0, 10.0, 1), Tick(1, 14.0, 2), Tick(2, 8.0, 1), Tick(3, 12.0, 1)]
    (bar,) = list(bars(ticks, period=60))
    assert bar.start == 0
    assert bar.open == 10.0
    assert bar.high == 14.0
    assert bar.low == 8.0
    assert bar.close == 12.0
    assert bar.volume == 5
    # Σ price·qty = 10 + 28 + 8 + 12 = 58 ; Σ qty = 5
    assert bar.vwap == pytest.approx(58.0 / 5)


def test_splits_into_windows_by_timestamp():
    ticks = [Tick(0, 10.0, 1), Tick(59, 11.0, 1), Tick(60, 20.0, 1), Tick(125, 30.0, 1)]
    out = list(bars(ticks, period=60))
    assert [b.start for b in out] == [0, 60, 120]


def test_open_first_close_last_high_max_low_min():
    ticks = [Tick(0, 5.0, 1), Tick(1, 9.0, 1), Tick(2, 3.0, 1), Tick(3, 7.0, 1)]
    (bar,) = list(bars(ticks, period=60))
    assert (bar.open, bar.close, bar.high, bar.low) == (5.0, 7.0, 9.0, 3.0)


def test_vwap_weights_by_quantity():
    # 10 @ 2 and 20 @ 1 -> (10*2 + 20*1) / (2 + 1) = 40 / 3
    ticks = [Tick(0, 10.0, 2), Tick(1, 20.0, 1)]
    (bar,) = list(bars(ticks, period=60))
    assert bar.vwap == pytest.approx(40.0 / 3)
    assert bar.volume == 3


def test_final_partial_bar_is_flushed():
    ticks = [Tick(0, 10.0, 1), Tick(60, 20.0, 1)]
    out = list(bars(ticks, period=60))
    assert len(out) == 2
    assert out[-1] == Bar(60, 20.0, 20.0, 20.0, 20.0, 1, 20.0)


def test_is_lazy_first_bar_without_consuming_everything():
    def stream() -> Iterator[Tick]:
        yield Tick(0, 10.0, 1)
        yield Tick(1, 12.0, 1)
        yield Tick(60, 20.0, 1)  # closes the first bar
        raise AssertionError("generator was fully consumed — aggregation is not lazy")

    first = next(bars(stream(), period=60))
    assert first.start == 0
    assert first.open == 10.0
    assert first.close == 12.0


def test_empty_input_yields_no_bars():
    assert list(bars([], period=60)) == []


def test_non_positive_period_raises():
    with pytest.raises(ValueError):
        # generators defer their body, so drive it to trigger the guard
        next(bars([Tick(0, 1.0, 1)], period=0))
    with pytest.raises(ValueError):
        next(bars([Tick(0, 1.0, 1)], period=-5))
