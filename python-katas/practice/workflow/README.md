# Order State Machine

> A trading order's lifecycle as a guarded state machine — accept, fill (in pieces), cancel, reject — where an illegal transition is a real trade the desk never intended.

## The problem

A trading order moves through a fixed lifecycle. It starts `NEW`; the venue either `ACCEPTED` or
`REJECTED` it; an accepted order fills — possibly in pieces (`PARTIALLY_FILLED`) until fully
`FILLED` — or is `CANCELLED` before completing. `FILLED`, `CANCELLED` and `REJECTED` are **terminal**:
nothing more can happen. Each incoming market event is only legal from certain states, and a fill
must never push the filled quantity past the order's total.

Write the pure transition function that drives this machine.

## Requirements

Implement `apply(order, event) -> Order` so that:

- `NEW` + `Accept` → `ACCEPTED` (same total); `NEW` + `Reject` → `REJECTED`; `NEW` + `Cancel` →
  `CANCELLED`; `NEW` + `Fill` → `IllegalTransition`.
- `ACCEPTED` + `Fill(qty)`: let `nf = filled + qty`; if `nf > total` → `Overfill`; if `nf == total` →
  `FILLED` (`filled = total`); else → `PARTIALLY_FILLED` (`filled = nf`).
- `ACCEPTED` + `Cancel` → `CANCELLED`; `ACCEPTED` + `Accept`/`Reject` → `IllegalTransition`.
- `PARTIALLY_FILLED` + `Fill(qty)`: same `nf` logic; `+ Cancel` → `CANCELLED`; anything else →
  `IllegalTransition`.
- `FILLED` / `CANCELLED` / `REJECTED` are terminal: **any** event → `IllegalTransition`.
- `apply` returns a **new** frozen `Order` and never mutates its input.

## What you implement

- `apply(order, event) -> Order`.

`OrderState`, `Order`, the four event dataclasses (`Accept`, `Fill`, `Cancel`, `Reject`), the `Event`
union, and the `IllegalTransition` / `Overfill` exceptions are all provided.

## The real challenge

- **Enums for states.** `OrderState` names the lifecycle; matching on it keeps the rule table
  readable instead of a soup of boolean flags.
- **Frozen dataclasses + `replace`.** `Order` is immutable — build the next state with
  `dataclasses.replace(order, state=..., filled=...)` rather than mutating. A pure `(state, event) →
  Order` function is trivial to test and safe to share/log.
- **Structural pattern matching.** Express the whole rule table as one `match (order.state, event):`.
  Use **class patterns** to destructure events (`Fill(qty=q)` binds the size), **OR-patterns** to
  collapse states that share a rule (`ACCEPTED | PARTIALLY_FILLED`), and a final `case _` for
  everything illegal.
- **Non-exhaustiveness caveat.** Python's `match` does **not** enforce exhaustiveness — an unhandled
  `(state, event)` pair silently falls through and returns `None`. The `case _` catch-all is
  deliberate: it's what turns an unhandled pair into a loud `IllegalTransition`.
- **The money angle.** An illegal transition that slips through is a real trade: filling a cancelled
  order, or an `Overfill` past `total`, books quantity the desk never meant to trade — an unhedged
  position and real P&L. The guards raise rather than clamp for exactly this reason.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_workflow.py` in this
directory (cover the full happy path, exact one-shot fill, every cancel/reject entry point, both
overfill shapes, and every terminal state × event), then:

```
cd python-katas && .venv/bin/pytest practice/workflow
```
Compare against the reference: `.venv/bin/pytest solution/workflow`.

## Reference

Worked solution: `solution/workflow/`.

Extension: the **typestate** pattern — make each state a distinct class (`NewOrder`, `AcceptedOrder`,
…) whose methods only expose the legal transitions, so illegal ones fail to *type-check* and never
reach runtime. Weigh the trade-off (type explosion; you still need a runtime `match` to narrow a
wire event into a concrete state).

Background: [Python `match` statement tutorial](https://docs.python.org/3/tutorial/controlflow.html#match-statements).
