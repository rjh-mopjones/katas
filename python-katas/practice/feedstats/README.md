# Market-Data Stream Aggregator

> A live trade feed emits ticks all session long; traders consume one-minute (or five-second) bars — roll the unbounded firehose into OHLC + VWAP bars as they stream, without ever holding the whole session in RAM.

## The problem

A market-data feed emits an unbounded stream of trade **ticks**, one per print on the tape, each a
`(ts, price, qty)`. Nobody charts raw ticks; they chart **bars** — the open/high/low/close, the
volume, and the volume-weighted average price (VWAP) over each fixed-length window. Your job is the
aggregator that turns the tick stream into a bar stream, emitting each bar the moment it is complete
and holding only the bar currently being built in memory.

## Requirements

- Bucket ticks by `start = (ts // period) * period`. Ticks arrive **time-ordered**.
- Each `Bar`: `open` = first tick's price in the window, `close` = last, `high` = max, `low` = min,
  `volume` = `sum(qty)`, `vwap` = `sum(price*qty) / sum(qty)` over the window.
- **Stream, don't buffer.** Yield each completed bar as soon as a tick from a *later* window arrives;
  flush the final (possibly partial) bar when the stream ends. Hold only the in-progress bar — O(1)
  space in the number of ticks. Do **not** collect the input into a list.
- Empty input yields no bars. `period <= 0` raises `ValueError`.

## What you implement

- `bars(ticks: Iterable[Tick], period: int) -> Iterator[Bar]`.

The `Tick` and `Bar` dataclasses are provided. You design the aggregation.

## The real challenge

- **Make it a generator, not a list-builder.** `yield` each bar as it completes; never read the whole
  feed first. A list forces the entire (possibly unbounded) session into memory before you can return
  anything — a generator is lazy and pull-based, computes each bar on demand, composes with `for` /
  `itertools` / an `islice` *take*, and stays O(1) in memory.
- **One tick of look-ahead is the whole trick.** A bar is only *done* once you see a tick belonging to
  a later window — and that same tick is the first tick of the next bar. So keep a small state machine:
  accumulate running open/high/low/close, volume, and the two VWAP sums (`Σ price·qty`, `Σ qty`); when
  a tick crosses the boundary, `yield` the finished bar and re-seed the accumulator from that tick. No
  buffer of ticks — just the running totals plus the one boundary tick.
- **Don't forget the tail.** When the stream ends, the in-progress bar has never seen a "later" tick —
  `yield` it after the loop, or you silently drop the last window.
- **Money angle.** VWAP is an execution benchmark: fills are scored against it and it feeds order
  slicers. Aggregating a live feed in one streaming pass lets you bar up an all-day tape at low latency
  without holding the session in RAM — and a wrong window or weighting misprices every downstream bar.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_feedstats.py` in this
directory (cover single-window OHLC+VWAP, splitting by timestamp, the quantity-weighted VWAP math,
laziness, empty input, and `period <= 0`), then:

```
cd python-katas && .venv/bin/pytest practice/feedstats
```
Compare against the reference: `.venv/bin/pytest solution/feedstats`.

## Reference

Worked solution: `solution/feedstats/`.

Extension: aggregate a **multi-symbol** feed by keying windows per symbol with `itertools.groupby`;
or compose a **rolling-VWAP** generator on top of `bars` (a sliding N-bar VWAP that consumes the bar
stream lazily, still O(N) memory).

Background: [Python generators](https://docs.python.org/3/howto/functional.html#generators).
