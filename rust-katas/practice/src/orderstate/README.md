# Order State Machine

> An order on a matching engine walks a fixed lifecycle — `New` → `Accepted` → fills → `Filled`, or `Cancelled`/`Rejected` along the way. Model the states as an enum and the transitions as one exhaustive `match`.

## The problem

An order has a small, closed set of lifecycle stages. Each stage carries its own data:
`PartiallyFilled { filled, total }` knows how much of the order is done. Given a current state and an
incoming event (accept, fill, cancel, reject), produce the next state — or refuse the transition when
it is illegal. `apply` is the whole transition table.

## Requirements

- `New { total }` + `Accept` → `Accepted { total }`; + `Reject` → `Rejected`; + `Cancel` →
  `Cancelled`; + `Fill` → `IllegalTransition`.
- `Accepted { total }` + `Fill { qty }`: `qty > total` → `Overfill`; `qty == total` → `Filled`; else
  `PartiallyFilled { filled: qty, total }`. `Accepted` + `Cancel` → `Cancelled`. `Accepted` +
  `Accept`/`Reject` → `IllegalTransition`.
- `PartiallyFilled { filled, total }` + `Fill { qty }`: let `nf = filled + qty`; `nf > total` →
  `Overfill`; `nf == total` → `Filled`; else `PartiallyFilled { filled: nf, total }`.
  `PartiallyFilled` + `Cancel` → `Cancelled`. Any other event → `IllegalTransition`.
- `Filled` / `Cancelled` / `Rejected` are **terminal**: any event → `IllegalTransition`.

## What you implement

- `fn apply(self, event: Event) -> Result<OrderState, TransitionError>` — consumes `self`, returns the
  next state.

`OrderState`, `Event`, and `TransitionError` (with its `Display` + `Error` impls) are provided
verbatim. You write `apply`.

## The real challenge

- **Enums + exhaustive `match` are the point.** The states are a closed set, so an `enum` names them
  exactly and `match` on `(state, event)` is the transition table. Rust `match` is *exhaustive* — the
  compiler forces every state to be handled. Add a seventh variant tomorrow and the crate won't
  compile until you decide what every event does to it; the illegal-transition table is code the
  compiler audits, not documentation you hope stays in sync.
- **Consuming `self` encodes the machine.** `apply(self, ..)` takes `self` by value, so the transition
  *moves* the old state out of existence — you physically cannot reuse the previous `OrderState` after
  transitioning. Move semantics make "a state, once left, is gone" a compile-time fact.
- **`Result` + a custom error + `#[non_exhaustive]`.** Illegal pairs return `Err(TransitionError)`, a
  domain enum that implements `std::error::Error` so callers can `?` or match it. It is marked
  `#[non_exhaustive]` so a new failure mode can be added later without breaking downstream matches.
- **Money angle.** The transition table *is* the risk control: an illegal transition slipping through
  is a double fill, a double payout, or a fill booked against an order the client already cancelled.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` in this
file (cover the full happy path, an exact one-shot fill, cancels/rejects, both `Overfill` cases, and
`IllegalTransition` from each terminal state), then:

```
cd rust-katas && cargo test -p practice orderstate
```

## Reference

Worked solution: `rust-katas/solution/src/orderstate/`.

Extension: the **typestate** pattern — make each state its own *type* (`Order<New>`, `Order<Accepted>`,
…) and give each type only the methods for its legal transitions, so `filled.cancel()` is a *compile*
error rather than a runtime `Err`. Weigh it against the enum: typestate wins when the state set is
fixed at compile time, but can't represent a state chosen at runtime.

Background: [The Rust Book — Defining an Enum & `match`](https://doc.rust-lang.org/book/ch06-00-enums.html).
