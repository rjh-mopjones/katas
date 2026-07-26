//! Candle Aggregator — fold a live tick stream into fixed-period OHLC candles, lazily.
//!
//! # The component
//!
//! A chart or a strategy consumes *candles* (open/high/low/close + volume over a fixed time window),
//! not raw ticks. [`candles`] takes any iterator of time-ordered [`Tick`]s and yields a stream of
//! [`Candle`]s, one per `period`-sized window. The point is that it is a **lazy adapter**: it holds
//! at most one in-progress candle and one look-ahead tick in memory, and produces each candle only
//! when you ask for it — it never buffers the whole day.
//!
//! # Why implement `Iterator` by hand
//!
//! You could `collect` the ticks into a `Vec`, group them, and return a `Vec<Candle>` — but that
//! forces the entire input into memory and computes everything eagerly, which is wrong for a feed
//! that runs all day. Implementing [`Iterator`] instead makes the aggregator *pull-based* and
//! composable: it plugs into `.map`, `.filter`, `.take`, `for`-loops, and any other adapter, and it
//! processes an unbounded stream in O(1) space.
//!
//! # The mechanic: one tick of look-ahead
//!
//! `next` is a little state machine. It folds ticks into the current candle while they fall in the
//! same window. The moment it reads a tick belonging to the *next* window it cannot un-read it, so it
//! stashes it in `pending` and returns the finished candle; the stashed tick opens the next one. When
//! the underlying iterator is exhausted, it emits the final partial candle, then `None`. This
//! "peek by stashing one item" pattern is how you write a windowing/`group-by` iterator without a
//! `Peekable` wrapper.
//!
//! # Generics
//!
//! [`candles`] is generic over `IntoIterator<Item = Tick>`, so it accepts a `Vec`, a slice's
//! `.copied()`, a channel drain, or another adapter — anything that yields ticks.
//!
//! # Alternative
//!
//! `itertools::chunk_by` / `group_by` gives a similar grouping for adjacent equal keys; here the key
//! is `ts / period` and we want streaming emission with OHLC folding, so a hand-written `Iterator` is
//! the clean fit. Money angle: aggregating a market-data feed into bars in real time, without ever
//! holding the whole session in RAM.

/// A single trade/quote print.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Tick {
    pub ts: u64,
    pub price: f64,
    pub qty: u64,
}

/// An OHLC candle over one `[start, start + period)` window.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Candle {
    pub start: u64,
    pub open: f64,
    pub high: f64,
    pub low: f64,
    pub close: f64,
    pub volume: u64,
}

/// A lazy iterator of [`Candle`]s over a tick stream. Construct it with [`candles`].
pub struct Candles<I: Iterator<Item = Tick>> {
    ticks: I,
    period: u64,
    pending: Option<Tick>,
    cur: Option<Candle>,
}

/// Aggregate `ticks` into OHLC candles of width `period`. Ticks are assumed time-ordered.
///
/// Panics if `period == 0`.
pub fn candles<I: IntoIterator<Item = Tick>>(ticks: I, period: u64) -> Candles<I::IntoIter> {
    assert!(period > 0, "period must be > 0");
    Candles {
        ticks: ticks.into_iter(),
        period,
        pending: None,
        cur: None,
    }
}

impl<I: Iterator<Item = Tick>> Iterator for Candles<I> {
    type Item = Candle;

    fn next(&mut self) -> Option<Candle> {
        loop {
            let tick = self.pending.take().or_else(|| self.ticks.next());
            match tick {
                Some(t) => {
                    let bucket = (t.ts / self.period) * self.period;
                    match &mut self.cur {
                        None => {
                            self.cur = Some(Candle {
                                start: bucket,
                                open: t.price,
                                high: t.price,
                                low: t.price,
                                close: t.price,
                                volume: t.qty,
                            });
                        }
                        Some(c) if c.start == bucket => {
                            c.high = c.high.max(t.price);
                            c.low = c.low.min(t.price);
                            c.close = t.price;
                            c.volume += t.qty;
                        }
                        Some(_) => {
                            // `t` belongs to a new window: emit the finished candle, keep `t`.
                            self.pending = Some(t);
                            return self.cur.take();
                        }
                    }
                }
                None => return self.cur.take(), // stream ended: flush the last candle, then None
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tick(ts: u64, price: f64, qty: u64) -> Tick {
        Tick { ts, price, qty }
    }

    #[test]
    fn empty_stream_yields_no_candles() {
        let out: Vec<Candle> = candles(Vec::<Tick>::new(), 10).collect();
        assert!(out.is_empty());
    }

    #[test]
    fn single_window_ohlc_and_volume() {
        let ticks = vec![tick(0, 10.0, 1), tick(3, 12.0, 2), tick(9, 8.0, 3)];
        let out: Vec<Candle> = candles(ticks, 10).collect();
        assert_eq!(
            out,
            vec![Candle {
                start: 0,
                open: 10.0,
                high: 12.0,
                low: 8.0,
                close: 8.0,
                volume: 6
            }]
        );
    }

    #[test]
    fn splits_into_windows_by_ts() {
        let ticks = vec![
            tick(0, 10.0, 1),
            tick(9, 8.0, 3),
            tick(10, 11.0, 1),
            tick(15, 9.0, 1),
            tick(23, 20.0, 5),
        ];
        let out: Vec<Candle> = candles(ticks, 10).collect();
        assert_eq!(
            out,
            vec![
                Candle {
                    start: 0,
                    open: 10.0,
                    high: 10.0,
                    low: 8.0,
                    close: 8.0,
                    volume: 4
                },
                Candle {
                    start: 10,
                    open: 11.0,
                    high: 11.0,
                    low: 9.0,
                    close: 9.0,
                    volume: 2
                },
                Candle {
                    start: 20,
                    open: 20.0,
                    high: 20.0,
                    low: 20.0,
                    close: 20.0,
                    volume: 5
                },
            ]
        );
    }

    #[test]
    fn open_is_first_close_is_last() {
        let ticks = vec![
            tick(1, 5.0, 1),
            tick(2, 7.0, 1),
            tick(3, 6.0, 1),
            tick(4, 4.0, 1),
        ];
        let out: Vec<Candle> = candles(ticks, 100).collect();
        assert_eq!(out.len(), 1);
        assert_eq!(out[0].open, 5.0);
        assert_eq!(out[0].close, 4.0);
        assert_eq!(out[0].high, 7.0);
        assert_eq!(out[0].low, 4.0);
    }

    #[test]
    fn is_lazy_and_composes_with_adapters() {
        // Take only the first candle without consuming the rest eagerly.
        let ticks = vec![tick(0, 1.0, 1), tick(1, 2.0, 1), tick(10, 3.0, 1)];
        let first = candles(ticks, 10).next().unwrap();
        assert_eq!(first.start, 0);
        assert_eq!(first.close, 2.0);
    }

    #[test]
    #[should_panic]
    fn zero_period_panics() {
        let _ = candles(vec![tick(0, 1.0, 1)], 0);
    }
}
