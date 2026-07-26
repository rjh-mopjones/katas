# Candle Aggregator

> Fold a live market-data tick stream into fixed-period OHLC candles — lazily, without ever buffering the whole day.

## The problem

A chart or a strategy consumes *candles* (open/high/low/close + total volume over a fixed time
window), not raw ticks. Turn an iterator of time-ordered `Tick`s into a stream of `Candle`s, one per
`period`-wide window (a tick at `ts` belongs to window `start = (ts / period) * period`). It must be a
lazy `Iterator` — hold only the in-progress candle in memory, emit each candle on demand.

## Requirements

- `candles(ticks, period)` returns something that implements `Iterator<Item = Candle>`.
- Each candle covers `[start, start + period)`: `open` = first tick's price, `close` = last, `high` =
  max, `low` = min, `volume` = sum of `qty`.
- Ticks are time-ordered; a tick in a new window closes the current candle and opens the next.
- The final (partial) window is emitted when the input ends.
- Empty input yields no candles. `period == 0` panics.

## What you implement

- `fn candles<I: IntoIterator<Item = Tick>>(ticks: I, period: u64) -> Candles<I::IntoIter>`
- `impl Iterator for Candles<I> { type Item = Candle; fn next(&mut self) -> Option<Candle> }`

`Tick` and `Candle` are provided verbatim. You design `Candles`'s fields and write `next`.

## The real challenge

- **Implement `Iterator` by hand.** Don't `collect` into a `Vec` and group — that's eager and O(n)
  space. A real `Iterator` is pull-based, composes with `.map`/`.take`/`for`, and runs in O(1) space
  over an unbounded stream.
- **One tick of look-ahead.** `next` folds ticks into the current candle until it reads one in the
  *next* window — which it can't un-read. Stash that tick, return the finished candle, and let the
  stashed tick open the next one. (This is the windowing/`group-by` pattern without `Peekable`.)
- **Flush at the end.** When the underlying iterator returns `None`, emit the final in-progress candle
  once, then `None`.
- **Generics.** Accept any `IntoIterator<Item = Tick>` so a `Vec`, a `.copied()` slice, or another
  adapter all work.
- **Money angle.** Aggregating a live feed into bars in real time, without holding the session in RAM.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests`
(check OHLC/volume, window splitting, the final partial candle, and that `.next()` works without
consuming the whole stream), then:

```
cd rust-katas && cargo test -p practice candles
```

## Reference

Worked solution: `rust-katas/solution/src/candles/`.

Extension: add a rolling VWAP field to `Candle`; or make it a generic adapter method on any
`Iterator<Item = Tick>` via an extension trait (`ticks.candles(period)`).

Background: [The Rust Book — Implementing the `Iterator` trait](https://doc.rust-lang.org/book/ch13-02-iterators.html).
