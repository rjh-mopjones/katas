/// A single trade/quote print. Provided verbatim.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Tick {
    pub ts: u64,
    pub price: f64,
    pub qty: u64,
}

/// An OHLC candle over one `[start, start + period)` window. Provided verbatim.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Candle {
    pub start: u64,
    pub open: f64,
    pub high: f64,
    pub low: f64,
    pub close: f64,
    pub volume: u64,
}

/// A lazy iterator of [`Candle`]s over a tick stream. You design the internal state.
pub struct Candles<I: Iterator<Item = Tick>> {
    _ticks: I,
    _period: u64,
}

/// Aggregate `ticks` into OHLC candles of width `period`. Ticks are assumed time-ordered.
pub fn candles<I: IntoIterator<Item = Tick>>(ticks: I, period: u64) -> Candles<I::IntoIter> {
    Candles {
        _ticks: ticks.into_iter(),
        _period: period,
    }
}

impl<I: Iterator<Item = Tick>> Iterator for Candles<I> {
    type Item = Candle;

    fn next(&mut self) -> Option<Candle> {
        todo!("aggregate ticks into fixed-period OHLC candles, lazily")
    }
}
