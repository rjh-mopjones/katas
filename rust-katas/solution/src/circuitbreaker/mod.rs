//! Circuit Breaker — a resilience wrapper that stops hammering a failing dependency.
//!
//! # The scenario
//!
//! A service calls a flaky downstream (a payment gateway, a pricing API, a venue). When that
//! dependency starts failing, retrying every call makes things *worse*: threads pile up on slow or
//! dead sockets, latency spikes, and the caller drags itself down with the dependency. The circuit
//! breaker sits in front of the call and watches the failure stream. After enough consecutive
//! failures it **trips open** and *fast-fails* every subsequent call without touching the downstream
//! — freeing callers instantly instead of blocking them on a doomed request. After a `cooldown` it
//! lets a few probe calls through; if they succeed it **closes** again, if one fails it re-opens.
//!
//! # The Rust idiom: a three-state machine as an `enum` + `match`
//!
//! The breaker is a textbook finite state machine with three states, modelled as [`State`]:
//!   - **`Closed`** — normal. Run the call. Count *consecutive* failures; any success resets the
//!     count. On the `failure_threshold`-th consecutive failure, go **`Open`** and stamp the time.
//!   - **`Open`** — tripped. Do **not** run the call; return [`CallError::Open`] immediately. Once
//!     `cooldown` has elapsed since it opened, the next call/observation moves it to **`HalfOpen`**.
//!   - **`HalfOpen`** — probing. Run the call. Count consecutive successes; on the
//!     `success_threshold`-th, go **`Closed`**. The *first* failure re-opens it and restarts the timer.
//!
//! Encoding the state as an `enum` and dispatching on it with `match` makes every legal transition
//! explicit and every illegal one unrepresentable — you can't be "half-open with a failure count"
//! by accident. This is Rust's answer to the tangle of `boolean isOpen; long openedAt;` flags you
//! see in hand-rolled breakers.
//!
//! # Thread-safe state via a `Mutex`
//!
//! One breaker is shared across every worker thread, so all mutable state — the current [`State`],
//! the counters, and the open-timestamp — lives inside a single `Mutex<Inner>`. Each `call` locks,
//! decides what to do (and runs the wrapped closure while holding the lock so the transition it
//! triggers stays atomic), then unlocks. Consecutive-count semantics need this: two threads must not
//! both see "one below threshold" and both trip.
//!
//! # The injected clock makes time testable
//!
//! Cooldown expiry is *time-dependent*, and a test that relied on `Instant::now()` would have to
//! `sleep` — slow and flaky. Instead the clock is injected as a `Box<dyn Fn() -> Instant + Send +
//! Sync>`. Production passes `Instant::now`; tests pass a closure reading a shared, hand-advanced
//! `Instant` so they can jump the cooldown forward deterministically with zero real waiting.
//!
//! # `CallError`: fast-fail vs. a real failure
//!
//! [`CallError`] distinguishes the two outcomes a caller must handle differently:
//!   - [`CallError::Open`] — the breaker was open, so `f` was **not** invoked. A signal to shed load,
//!     serve a cached/fallback value, or fail fast.
//!   - [`CallError::Inner(e)`] — `f` ran and returned `Err(e)`; the real downstream error is preserved.
//!
//! # Alternatives
//!
//! This breaker trips on a *consecutive* failure count — simple and predictable. Production breakers
//! often trip on a **rolling-window failure rate** (e.g. >50% of the last 100 calls) so a steady low
//! background error rate doesn't accumulate into a trip, and add a per-call timeout that counts a slow
//! call as a failure. The `failsafe` crate and `tower`'s middleware provide these off the shelf; this
//! kata is their core state machine written by hand.

use std::sync::Mutex;
use std::time::{Duration, Instant};

/// The three states of the breaker. See the module docs for the transition rules.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum State {
    /// Normal operation — calls run and failures are counted.
    Closed,
    /// Tripped — calls fast-fail without touching the downstream.
    Open,
    /// Probing after cooldown — a few calls run to test recovery.
    HalfOpen,
}

/// The failure outcome of [`CircuitBreaker::call`].
#[derive(Debug, PartialEq, Eq)]
pub enum CallError<E> {
    /// The breaker was open: the wrapped call was **not** invoked.
    Open,
    /// The wrapped call ran and returned `Err(E)`.
    Inner(E),
}

/// All mutable state, guarded together so transitions are atomic.
struct Inner {
    state: State,
    /// Consecutive failures in `Closed`.
    failures: u32,
    /// Consecutive successes in `HalfOpen`.
    successes: u32,
    /// When the breaker last opened — used to compute cooldown expiry.
    opened_at: Option<Instant>,
}

/// A shared, thread-safe circuit breaker. Wrap it in an `Arc` (or take it by `&`) to hand to workers.
pub struct CircuitBreaker {
    inner: Mutex<Inner>,
    failure_threshold: u32,
    success_threshold: u32,
    cooldown: Duration,
    clock: Box<dyn Fn() -> Instant + Send + Sync>,
}

impl CircuitBreaker {
    /// Build a breaker.
    ///
    /// - `failure_threshold`: consecutive failures in `Closed` that trip it to `Open`.
    /// - `success_threshold`: consecutive successes in `HalfOpen` that close it.
    /// - `cooldown`: how long to stay `Open` before probing (`HalfOpen`).
    /// - `clock`: source of "now" — `Instant::now` in production, a fake in tests.
    pub fn new(
        failure_threshold: u32,
        success_threshold: u32,
        cooldown: Duration,
        clock: impl Fn() -> Instant + Send + Sync + 'static,
    ) -> Self {
        CircuitBreaker {
            inner: Mutex::new(Inner {
                state: State::Closed,
                failures: 0,
                successes: 0,
                opened_at: None,
            }),
            failure_threshold,
            success_threshold,
            cooldown,
            clock: Box::new(clock),
        }
    }

    /// The current state, after applying any pending cooldown expiry (`Open` → `HalfOpen`).
    pub fn state(&self) -> State {
        let mut inner = self.inner.lock().unwrap();
        self.maybe_half_open(&mut inner);
        inner.state
    }

    /// Run `f` through the breaker.
    ///
    /// - `Closed`/`HalfOpen`: invokes `f`, records the outcome, returns `Ok(v)` or
    ///   `Err(CallError::Inner(e))`.
    /// - `Open` (cooldown not yet elapsed): returns `Err(CallError::Open)` without invoking `f`.
    pub fn call<T, E>(&self, f: impl FnOnce() -> Result<T, E>) -> Result<T, CallError<E>> {
        let mut inner = self.inner.lock().unwrap();
        self.maybe_half_open(&mut inner);

        if inner.state == State::Open {
            return Err(CallError::Open);
        }

        // Closed or HalfOpen: run the call while holding the lock so the state transition it triggers
        // stays atomic with respect to other threads.
        match f() {
            Ok(v) => {
                self.on_success(&mut inner);
                Ok(v)
            }
            Err(e) => {
                self.on_failure(&mut inner);
                Err(CallError::Inner(e))
            }
        }
    }

    /// If the breaker is `Open` and the cooldown has elapsed, move it to `HalfOpen`.
    fn maybe_half_open(&self, inner: &mut Inner) {
        if inner.state == State::Open {
            let opened_at = inner.opened_at.expect("Open implies a recorded open time");
            if (self.clock)().duration_since(opened_at) >= self.cooldown {
                inner.state = State::HalfOpen;
                inner.successes = 0;
            }
        }
    }

    fn on_success(&self, inner: &mut Inner) {
        match inner.state {
            State::Closed => inner.failures = 0,
            State::HalfOpen => {
                inner.successes += 1;
                if inner.successes >= self.success_threshold {
                    inner.state = State::Closed;
                    inner.failures = 0;
                    inner.successes = 0;
                    inner.opened_at = None;
                }
            }
            State::Open => {}
        }
    }

    fn on_failure(&self, inner: &mut Inner) {
        match inner.state {
            State::Closed => {
                inner.failures += 1;
                if inner.failures >= self.failure_threshold {
                    self.trip(inner);
                }
            }
            // The first failure while probing re-opens the breaker.
            State::HalfOpen => self.trip(inner),
            State::Open => {}
        }
    }

    fn trip(&self, inner: &mut Inner) {
        inner.state = State::Open;
        inner.failures = 0;
        inner.successes = 0;
        inner.opened_at = Some((self.clock)());
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::{Arc, Mutex};

    /// A hand-advanced clock: tests move time forward explicitly, no real sleeps.
    fn fake_clock() -> (
        Arc<Mutex<Instant>>,
        impl Fn() -> Instant + Send + Sync + 'static,
    ) {
        let t = Arc::new(Mutex::new(Instant::now()));
        let clock = {
            let t = t.clone();
            move || *t.lock().unwrap()
        };
        (t, clock)
    }

    fn advance(t: &Arc<Mutex<Instant>>, d: Duration) {
        *t.lock().unwrap() += d;
    }

    const OK: Result<i32, &str> = Ok(1);
    const BAD: Result<i32, &str> = Err("boom");

    #[test]
    fn closed_passes_through_and_returns_ok() {
        let (_t, clock) = fake_clock();
        let cb = CircuitBreaker::new(3, 2, Duration::from_secs(30), clock);
        assert_eq!(cb.state(), State::Closed);
        assert_eq!(cb.call(|| OK), Ok(1));
        assert_eq!(cb.state(), State::Closed);
    }

    #[test]
    fn consecutive_failures_trip_to_open() {
        let (_t, clock) = fake_clock();
        let cb = CircuitBreaker::new(3, 2, Duration::from_secs(30), clock);
        assert_eq!(cb.call(|| BAD), Err(CallError::Inner("boom")));
        assert_eq!(cb.call(|| BAD), Err(CallError::Inner("boom")));
        assert_eq!(cb.state(), State::Closed); // still one below threshold
        assert_eq!(cb.call(|| BAD), Err(CallError::Inner("boom")));
        assert_eq!(cb.state(), State::Open); // third consecutive failure trips it
    }

    #[test]
    fn open_does_not_invoke_f_and_fast_fails() {
        let (_t, clock) = fake_clock();
        let cb = CircuitBreaker::new(1, 1, Duration::from_secs(30), clock);
        cb.call(|| BAD).ok(); // trips immediately (threshold 1)
        assert_eq!(cb.state(), State::Open);

        let invoked = AtomicUsize::new(0);
        let res: Result<i32, CallError<&str>> = cb.call(|| {
            invoked.fetch_add(1, Ordering::SeqCst);
            OK
        });
        assert_eq!(res, Err(CallError::Open));
        assert_eq!(
            invoked.load(Ordering::SeqCst),
            0,
            "f must not run while open"
        );
    }

    #[test]
    fn cooldown_elapsing_moves_to_half_open() {
        let (t, clock) = fake_clock();
        let cb = CircuitBreaker::new(1, 2, Duration::from_secs(30), clock);
        cb.call(|| BAD).ok();
        assert_eq!(cb.state(), State::Open);

        advance(&t, Duration::from_secs(29));
        assert_eq!(cb.state(), State::Open); // cooldown not yet elapsed
        advance(&t, Duration::from_secs(1));
        assert_eq!(cb.state(), State::HalfOpen); // exactly at cooldown
    }

    #[test]
    fn half_open_reaching_success_threshold_closes() {
        let (t, clock) = fake_clock();
        let cb = CircuitBreaker::new(1, 2, Duration::from_secs(30), clock);
        cb.call(|| BAD).ok();
        advance(&t, Duration::from_secs(30));
        assert_eq!(cb.state(), State::HalfOpen);

        assert_eq!(cb.call(|| OK), Ok(1));
        assert_eq!(cb.state(), State::HalfOpen); // one success, need two
        assert_eq!(cb.call(|| OK), Ok(1));
        assert_eq!(cb.state(), State::Closed); // second success closes it
    }

    #[test]
    fn failure_in_half_open_reopens_and_restarts_timer() {
        let (t, clock) = fake_clock();
        let cb = CircuitBreaker::new(1, 2, Duration::from_secs(30), clock);
        cb.call(|| BAD).ok();
        advance(&t, Duration::from_secs(30));
        assert_eq!(cb.state(), State::HalfOpen);

        assert_eq!(cb.call(|| BAD), Err(CallError::Inner("boom")));
        assert_eq!(cb.state(), State::Open); // one failure while probing re-opens

        advance(&t, Duration::from_secs(29));
        assert_eq!(cb.state(), State::Open); // timer restarted, cooldown counts from re-open
        advance(&t, Duration::from_secs(1));
        assert_eq!(cb.state(), State::HalfOpen);
    }

    #[test]
    fn success_in_closed_resets_the_failure_count() {
        let (_t, clock) = fake_clock();
        let cb = CircuitBreaker::new(3, 2, Duration::from_secs(30), clock);
        // Two failures (threshold - 1), then a success clears the count...
        cb.call(|| BAD).ok();
        cb.call(|| BAD).ok();
        assert_eq!(cb.call(|| OK), Ok(1));
        // ...so two more failures should NOT trip it early.
        cb.call(|| BAD).ok();
        cb.call(|| BAD).ok();
        assert_eq!(cb.state(), State::Closed);
        cb.call(|| BAD).ok(); // now the third consecutive failure trips it
        assert_eq!(cb.state(), State::Open);
    }
}
