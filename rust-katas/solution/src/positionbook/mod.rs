//! Position Book — a thread-safe net-position keeper for a trading desk.
//!
//! # The component
//!
//! Many order-handler threads report [`Fill`]s (signed quantities: `+` bought, `-` sold) for many
//! symbols; a [`PositionBook`] keeps the running net position per symbol. A hedge moves quantity from
//! one symbol to another atomically. This is shared mutable state hammered by many threads — the
//! canonical "make it thread-safe, and don't deadlock" senior question.
//!
//! # What Rust does and does NOT give you here
//!
//! Rust's type system makes a **data race impossible**: to mutate an `i64` from multiple threads you
//! *must* wrap it (here in a `Mutex`), because `&mut` can't cross threads and `&` can't mutate. So the
//! Go/C++ "prove the torn read with a race detector" exercise doesn't exist — it wouldn't compile.
//! What the compiler does **not** save you from, and what this kata is about, are the two runtime
//! concurrency bugs that survive the borrow checker:
//!
//!   - **Lost update.** `apply` does a read-modify-write. If you clone the value out, drop the lock,
//!     add, and store back, two threads interleave and one update vanishes. The fix is to hold the
//!     lock *across* the whole RMW — `*guard += qty` — which this code does.
//!   - **Deadlock.** `hedge` locks *two* symbols. If thread 1 does `hedge("A","B")` and thread 2 does
//!     `hedge("B","A")` and each grabs its first lock, they wait on each other forever. The fix is a
//!     global lock order: always lock the lexicographically-smaller symbol first, whichever direction
//!     the hedge goes.
//!
//! # Design
//!
//! A `RwLock<HashMap<String, Arc<Mutex<i64>>>>` registry: reads of the map (the common case) share the
//! `RwLock`, and each symbol's position lives behind its *own* `Mutex` so unrelated symbols update in
//! parallel. Per-symbol locks are what create the deadlock hazard that `hedge` must order around; a
//! single `Mutex<HashMap<String,i64>>` would be simpler and deadlock-free but would serialise every
//! update through one lock. `.lock().unwrap()` propagates a *poisoned* mutex (a thread panicked while
//! holding it) — acceptable for this kata.
//!
//! # Alternative
//!
//! For a pure counter you could drop the `Mutex` for an `AtomicI64` per symbol (lock-free `apply`),
//! but a two-symbol `hedge` then needs a different atomicity story (a lock, or a CAS loop over both).
//! Sharding the map by hash is the usual next step for contention. Money angle: a lost fill is a wrong
//! position and a mis-hedged book; a deadlock is a wedged trading system at peak.

use std::collections::HashMap;
use std::sync::{Arc, Mutex, RwLock};

/// A signed position change for a symbol: `qty > 0` bought, `qty < 0` sold.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Fill {
    pub symbol: String,
    pub qty: i64,
}

/// A concurrency-safe net-position book. Share it across threads by reference (`&PositionBook`, it is
/// `Sync`) or via `Arc`.
#[derive(Default)]
pub struct PositionBook {
    positions: RwLock<HashMap<String, Arc<Mutex<i64>>>>,
}

impl PositionBook {
    pub fn new() -> Self {
        Self::default()
    }

    /// Get (or create) the per-symbol lock. Fast path takes only a read lock on the registry.
    fn slot(&self, symbol: &str) -> Arc<Mutex<i64>> {
        {
            let map = self.positions.read().unwrap();
            if let Some(slot) = map.get(symbol) {
                return Arc::clone(slot);
            }
        }
        let mut map = self.positions.write().unwrap();
        Arc::clone(
            map.entry(symbol.to_string())
                .or_insert_with(|| Arc::new(Mutex::new(0))),
        )
    }

    /// Apply a fill to its symbol's net position. The read-modify-write is done under the held lock,
    /// so concurrent applies never lose an update.
    pub fn apply(&self, fill: &Fill) {
        let slot = self.slot(&fill.symbol);
        let mut pos = slot.lock().unwrap();
        *pos += fill.qty;
    }

    /// Move `qty` from `from` to `to` atomically (`from -= qty`, `to += qty`). Locks the two symbols
    /// in a fixed global order (smaller name first) so opposing hedges can never deadlock.
    pub fn hedge(&self, from: &str, to: &str, qty: i64) {
        if from == to {
            return;
        }
        let from_slot = self.slot(from);
        let to_slot = self.slot(to);

        // Always acquire the lexicographically-smaller symbol's lock first.
        if from < to {
            let mut a = from_slot.lock().unwrap();
            let mut b = to_slot.lock().unwrap();
            *a -= qty;
            *b += qty;
        } else {
            let mut b = to_slot.lock().unwrap();
            let mut a = from_slot.lock().unwrap();
            *a -= qty;
            *b += qty;
        }
    }

    /// The current net position for `symbol` (0 if unknown).
    pub fn position(&self, symbol: &str) -> i64 {
        let map = self.positions.read().unwrap();
        match map.get(symbol) {
            Some(slot) => *slot.lock().unwrap(),
            None => 0,
        }
    }

    /// Sum of all net positions — invariant under `hedge`, useful for conservation checks.
    pub fn total(&self) -> i64 {
        let map = self.positions.read().unwrap();
        map.values().map(|s| *s.lock().unwrap()).sum()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Barrier;
    use std::thread;

    fn fill(symbol: &str, qty: i64) -> Fill {
        Fill {
            symbol: symbol.to_string(),
            qty,
        }
    }

    #[test]
    fn apply_accumulates_net_position() {
        let book = PositionBook::new();
        book.apply(&fill("VOD", 100));
        book.apply(&fill("VOD", -30));
        assert_eq!(book.position("VOD"), 70);
    }

    #[test]
    fn unknown_symbol_is_zero() {
        let book = PositionBook::new();
        assert_eq!(book.position("NONE"), 0);
    }

    #[test]
    fn hedge_moves_quantity_and_conserves_total() {
        let book = PositionBook::new();
        book.apply(&fill("A", 50));
        book.apply(&fill("B", 10));
        book.hedge("A", "B", 20);
        assert_eq!(book.position("A"), 30);
        assert_eq!(book.position("B"), 30);
        assert_eq!(book.total(), 60); // conserved
    }

    // Many threads apply to the same symbols at once; a lost-update bug would make the totals fall
    // short. Barrier-gated so every thread starts together (maximum contention). No sleeps.
    #[test]
    fn concurrent_applies_lose_no_updates() {
        const THREADS: usize = 8;
        const ITERS: i64 = 20_000;
        let symbols = ["A", "B", "C", "D"];

        let book = PositionBook::new();
        let barrier = Barrier::new(THREADS);

        thread::scope(|s| {
            for t in 0..THREADS {
                let book = &book;
                let barrier = &barrier;
                s.spawn(move || {
                    barrier.wait();
                    for i in 0..ITERS {
                        book.apply(&fill(symbols[(t + i as usize) % symbols.len()], 1));
                    }
                });
            }
        });

        // Each of THREADS threads did ITERS applies of +1, spread across the symbols.
        assert_eq!(book.total(), THREADS as i64 * ITERS);
    }

    // Opposing hedges run concurrently; a bad lock order would deadlock (the test would hang) and a
    // lost update would break conservation.
    #[test]
    fn concurrent_hedges_do_not_deadlock_and_conserve() {
        const THREADS: usize = 8;
        const ITERS: usize = 20_000;

        let book = PositionBook::new();
        book.apply(&fill("A", 1_000_000));
        book.apply(&fill("B", 1_000_000));
        let start_total = book.total();
        let barrier = Barrier::new(THREADS);

        thread::scope(|s| {
            for t in 0..THREADS {
                let book = &book;
                let barrier = &barrier;
                s.spawn(move || {
                    barrier.wait();
                    // Half the threads hedge A->B, half B->A — the classic deadlock setup.
                    let (from, to) = if t % 2 == 0 { ("A", "B") } else { ("B", "A") };
                    for _ in 0..ITERS {
                        book.hedge(from, to, 1);
                    }
                });
            }
        });

        assert_eq!(book.total(), start_total); // hedge conserves total
    }
}
