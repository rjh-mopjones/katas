/// A signed position change for a symbol: `qty > 0` bought, `qty < 0` sold. Provided verbatim.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Fill {
    pub symbol: String,
    pub qty: i64,
}

/// A concurrency-safe net-position book. You design the internals (the shared state + locking).
pub struct PositionBook;

impl PositionBook {
    pub fn new() -> Self {
        todo!("choose your shared-state representation")
    }

    /// Apply a fill to its symbol's net position.
    pub fn apply(&self, _fill: &Fill) {
        todo!()
    }

    /// Move `qty` from `from` to `to` atomically (`from -= qty`, `to += qty`).
    pub fn hedge(&self, _from: &str, _to: &str, _qty: i64) {
        todo!()
    }

    /// The current net position for `symbol` (0 if unknown).
    pub fn position(&self, _symbol: &str) -> i64 {
        todo!()
    }

    /// Sum of all net positions.
    pub fn total(&self) -> i64 {
        todo!()
    }
}
