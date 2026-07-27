use std::time::{Duration, Instant};

/// The three states of the breaker.
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

/// A shared, thread-safe circuit breaker. You design the internals (state machine + `Mutex` state).
pub struct CircuitBreaker {
    _clock: Box<dyn Fn() -> Instant + Send + Sync>,
}

impl CircuitBreaker {
    pub fn new(
        _failure_threshold: u32,
        _success_threshold: u32,
        _cooldown: Duration,
        clock: impl Fn() -> Instant + Send + Sync + 'static,
    ) -> Self {
        CircuitBreaker {
            _clock: Box::new(clock),
        }
    }

    pub fn state(&self) -> State {
        todo!()
    }

    pub fn call<T, E>(&self, _f: impl FnOnce() -> Result<T, E>) -> Result<T, CallError<E>> {
        todo!()
    }
}
