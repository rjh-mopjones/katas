use std::error::Error;
use std::fmt;

/// The lifecycle stage of an order. Each variant carries the data that stage needs. Provided verbatim.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum OrderState {
    New { total: u64 },
    Accepted { total: u64 },
    PartiallyFilled { filled: u64, total: u64 },
    Filled,
    Cancelled,
    Rejected,
}

/// An event the venue or client applies to an order. Provided verbatim.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Event {
    Accept,
    Fill { qty: u64 },
    Cancel,
    Reject,
}

/// Why a transition was refused. Provided verbatim (the public contract).
#[derive(Debug, Clone, PartialEq, Eq)]
#[non_exhaustive]
pub enum TransitionError {
    IllegalTransition { state: OrderState, event: Event },
    Overfill { filled: u64, qty: u64, total: u64 },
}

impl fmt::Display for TransitionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}

impl Error for TransitionError {}

impl OrderState {
    /// Apply `event`, consuming `self` and returning the next state, or a [`TransitionError`].
    pub fn apply(self, _event: Event) -> Result<OrderState, TransitionError> {
        todo!("implement the order lifecycle transition table")
    }
}
