//! Order State Machine — an order's lifecycle as an **enum**, transitions as an exhaustive `match`.
//!
//! # The component
//!
//! An order on a matching engine walks a fixed lifecycle: it is `New`, gets `Accepted`, takes fills
//! until it is `Filled`, or is `Cancelled`/`Rejected` along the way. [`OrderState`] models each stage
//! as an enum *variant that carries its own data* (`PartiallyFilled { filled, total }` knows how much
//! is done), and [`OrderState::apply`] is the whole transition table written as one `match` over
//! `(state, event)`.
//!
//! # Why an enum + exhaustive match
//!
//! The states are a closed set, so an `enum` names them exactly — no stringly-typed `status`, no
//! "impossible" `else` branch. `apply` matches on `self` and the incoming [`Event`]; because Rust
//! `match` is **exhaustive**, the compiler forces every state to be handled. Add a seventh variant
//! tomorrow (say `Suspended`) and the crate *won't compile* until you decide what every event does to
//! it. The illegal-transition table isn't documentation you hope stays in sync — it's the code, and
//! the compiler audits it for you.
//!
//! # Why `apply` consumes `self`
//!
//! `apply(self, ..)` takes `self` *by value* and returns the next state, so the transition **moves**
//! the old state out of existence. Once you've applied an event you physically cannot reuse the
//! previous `OrderState` — the move checker rejects it. A stale `Accepted` handle can't be fed a
//! second fill after the order already reached `Filled`, because that handle was consumed. Move
//! semantics encode "a state, once left, is gone" as a compile-time fact rather than a convention.
//!
//! # Errors
//!
//! Not every (state, event) pair is legal, so `apply` returns `Result<OrderState, TransitionError>`.
//! [`TransitionError`] is a domain enum implementing [`std::error::Error`], so callers can `?` it or
//! match the specific failure — an illegal transition versus an [`Overfill`](TransitionError::Overfill)
//! (a fill that would push filled quantity past the order total). It is marked `#[non_exhaustive]`:
//! callers outside this crate must keep a wildcard arm, so we can add a failure mode later without
//! breaking them.
//!
//! # Alternatives
//!
//! The stricter design is the **typestate** pattern: make each state its own *type* (`Order<New>`,
//! `Order<Accepted>`, …) and give each type only the methods for its legal transitions. Then
//! `filled_order.cancel()` isn't a runtime `Err` — it's a *compile* error, because `Order<Filled>`
//! has no `cancel`. The trade-off: typestate is unbeatable when the state set is fixed and known at
//! compile time, but it can't represent a state chosen at runtime (from a config, a wire message) and
//! it fans out into many types/generics. The runtime enum here keeps states first-class values you
//! can store, send, and pick dynamically, at the cost of a `Result` instead of a type error.
//!
//! # Money angle
//!
//! The transition table *is* the risk control. An illegal transition slipping through is a double
//! fill, a double payout, or a fill booked against an order the client already cancelled — so we make
//! "you cannot leave a terminal state" a thing the compiler and the `Result` both enforce, not a
//! comment someone deletes.

use std::error::Error;
use std::fmt;

/// The lifecycle stage of an order. Each variant carries the data that stage needs.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum OrderState {
    /// Freshly created, not yet acknowledged by the venue. `total` is the order quantity.
    New { total: u64 },
    /// Acknowledged and working, no fills yet.
    Accepted { total: u64 },
    /// Working with a running fill quantity; `filled < total`.
    PartiallyFilled { filled: u64, total: u64 },
    /// Fully filled — terminal.
    Filled,
    /// Cancelled before completion — terminal.
    Cancelled,
    /// Rejected by the venue — terminal.
    Rejected,
}

/// An event the venue or client applies to an order.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Event {
    /// Venue acknowledges the order.
    Accept,
    /// A fill of `qty` units arrives.
    Fill { qty: u64 },
    /// Client (or venue) cancels the order.
    Cancel,
    /// Venue rejects the order.
    Reject,
}

/// Why a transition was refused.
///
/// `#[non_exhaustive]`: adding a variant later is not a breaking change, so downstream `match`es must
/// carry a wildcard arm.
#[derive(Debug, Clone, PartialEq, Eq)]
#[non_exhaustive]
pub enum TransitionError {
    /// This event is not legal in this state (e.g. any event on a terminal state).
    IllegalTransition { state: OrderState, event: Event },
    /// A fill would push filled quantity past the order total.
    Overfill { filled: u64, qty: u64, total: u64 },
}

impl fmt::Display for TransitionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            TransitionError::IllegalTransition { state, event } => {
                write!(f, "illegal transition: {event:?} in state {state:?}")
            }
            TransitionError::Overfill { filled, qty, total } => write!(
                f,
                "overfill: filled {filled} + qty {qty} exceeds total {total}"
            ),
        }
    }
}

impl Error for TransitionError {}

impl OrderState {
    /// Apply `event`, consuming `self` and returning the next state, or a [`TransitionError`].
    ///
    /// Taking `self` by value means the old state is *moved out* — it cannot be reused after the
    /// transition, so a stale handle can never be fed a second event.
    pub fn apply(self, event: Event) -> Result<OrderState, TransitionError> {
        match (self, event) {
            // --- New -------------------------------------------------------------------------
            (OrderState::New { total }, Event::Accept) => Ok(OrderState::Accepted { total }),
            (OrderState::New { .. }, Event::Reject) => Ok(OrderState::Rejected),
            (OrderState::New { .. }, Event::Cancel) => Ok(OrderState::Cancelled),

            // --- Accepted --------------------------------------------------------------------
            (OrderState::Accepted { total }, Event::Fill { qty }) => fill(0, qty, total),
            (OrderState::Accepted { .. }, Event::Cancel) => Ok(OrderState::Cancelled),

            // --- PartiallyFilled -------------------------------------------------------------
            (OrderState::PartiallyFilled { filled, total }, Event::Fill { qty }) => {
                fill(filled, qty, total)
            }
            (OrderState::PartiallyFilled { .. }, Event::Cancel) => Ok(OrderState::Cancelled),

            // --- Everything else is illegal (terminal states + bad (state, event) pairs) -----
            (state, event) => Err(TransitionError::IllegalTransition { state, event }),
        }
    }
}

/// Advance a working order by `qty`, given the quantity `filled` so far and the order `total`.
///
/// Kept as a free helper so the `Accepted` (filled == 0) and `PartiallyFilled` arms share one rule.
fn fill(filled: u64, qty: u64, total: u64) -> Result<OrderState, TransitionError> {
    let next_filled = filled + qty;
    if next_filled > total {
        Err(TransitionError::Overfill { filled, qty, total })
    } else if next_filled == total {
        Ok(OrderState::Filled)
    } else {
        Ok(OrderState::PartiallyFilled {
            filled: next_filled,
            total,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn full_happy_path_new_accept_partial_then_fill_to_filled() {
        let s = OrderState::New { total: 10 };
        let s = s.apply(Event::Accept).unwrap();
        assert_eq!(s, OrderState::Accepted { total: 10 });
        let s = s.apply(Event::Fill { qty: 4 }).unwrap();
        assert_eq!(
            s,
            OrderState::PartiallyFilled {
                filled: 4,
                total: 10
            }
        );
        let s = s.apply(Event::Fill { qty: 6 }).unwrap();
        assert_eq!(s, OrderState::Filled);
    }

    #[test]
    fn exact_fill_in_one_shot_reaches_filled() {
        let s = OrderState::Accepted { total: 10 };
        let s = s.apply(Event::Fill { qty: 10 }).unwrap();
        assert_eq!(s, OrderState::Filled);
    }

    #[test]
    fn cancel_from_new() {
        let s = OrderState::New { total: 5 };
        assert_eq!(s.apply(Event::Cancel), Ok(OrderState::Cancelled));
    }

    #[test]
    fn cancel_from_accepted() {
        let s = OrderState::Accepted { total: 5 };
        assert_eq!(s.apply(Event::Cancel), Ok(OrderState::Cancelled));
    }

    #[test]
    fn cancel_from_partially_filled() {
        let s = OrderState::PartiallyFilled {
            filled: 2,
            total: 5,
        };
        assert_eq!(s.apply(Event::Cancel), Ok(OrderState::Cancelled));
    }

    #[test]
    fn reject_from_new() {
        let s = OrderState::New { total: 5 };
        assert_eq!(s.apply(Event::Reject), Ok(OrderState::Rejected));
    }

    #[test]
    fn overfill_from_accepted_is_rejected() {
        let s = OrderState::Accepted { total: 10 };
        assert_eq!(
            s.apply(Event::Fill { qty: 11 }),
            Err(TransitionError::Overfill {
                filled: 0,
                qty: 11,
                total: 10,
            })
        );
    }

    #[test]
    fn overfill_from_partially_filled_is_rejected() {
        let s = OrderState::PartiallyFilled {
            filled: 8,
            total: 10,
        };
        assert_eq!(
            s.apply(Event::Fill { qty: 3 }),
            Err(TransitionError::Overfill {
                filled: 8,
                qty: 3,
                total: 10,
            })
        );
    }

    #[test]
    fn new_plus_fill_is_illegal() {
        let s = OrderState::New { total: 10 };
        assert_eq!(
            s.apply(Event::Fill { qty: 1 }),
            Err(TransitionError::IllegalTransition {
                state: OrderState::New { total: 10 },
                event: Event::Fill { qty: 1 },
            })
        );
    }

    #[test]
    fn accepted_plus_accept_or_reject_is_illegal() {
        assert_eq!(
            OrderState::Accepted { total: 10 }.apply(Event::Accept),
            Err(TransitionError::IllegalTransition {
                state: OrderState::Accepted { total: 10 },
                event: Event::Accept,
            })
        );
        assert_eq!(
            OrderState::Accepted { total: 10 }.apply(Event::Reject),
            Err(TransitionError::IllegalTransition {
                state: OrderState::Accepted { total: 10 },
                event: Event::Reject,
            })
        );
    }

    #[test]
    fn terminal_states_reject_every_event() {
        for terminal in [
            OrderState::Filled,
            OrderState::Cancelled,
            OrderState::Rejected,
        ] {
            for event in [
                Event::Accept,
                Event::Fill { qty: 1 },
                Event::Cancel,
                Event::Reject,
            ] {
                assert_eq!(
                    terminal.clone().apply(event.clone()),
                    Err(TransitionError::IllegalTransition {
                        state: terminal.clone(),
                        event: event.clone(),
                    })
                );
            }
        }
    }

    #[test]
    fn error_implements_display() {
        let e = TransitionError::Overfill {
            filled: 8,
            qty: 3,
            total: 10,
        };
        assert_eq!(e.to_string(), "overfill: filled 8 + qty 3 exceeds total 10");
    }
}
