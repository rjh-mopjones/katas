# Bet State Machine

> A bet/order lifecycle state machine that stays correct when events are duplicated and arrive concurrently.

## The problem

A bet moves through a small lifecycle:

```
Pending  --Accept--> Accepted --Settle--> Settled   (terminal)
Pending  --Reject--> Rejected                        (terminal)
Pending  --Cancel--> Cancelled                       (terminal)
Accepted --Cancel--> Cancelled                       (terminal)
```

Each transition stands in for a real, money-moving side effect: `Settle` pays out
winnings, `Reject` releases the stake, `Cancel` voids and refunds. Lifecycle
events arrive over an unreliable transport with **at-least-once delivery**: the
same event can be redelivered (duplicated) and concurrent events for one bet can
race. Your machine must apply the right transition exactly once and reject the
rest.

## Requirements

- Track the current `State` of many bets, keyed by a string id.
- `Open` registers a new bet in `StatePending`; opening an existing id is an error
  (a duplicate `Open` must not reset a bet that has already progressed).
- `Apply` validates the event against a transition table and, if legal, moves the
  bet to the target state. Illegal transitions return `ErrIllegalTransition` and
  leave the state **unchanged**. Terminal states (Settled/Rejected/Cancelled)
  reject all further events.
- Idempotent redelivery: applying an event that targets the state the bet is
  **already in** (e.g. `Settle` on an already-Settled bet) is a no-op success —
  it returns the current state with a nil error and does **not** re-run the side
  effect. A *different* event against a terminal state is still illegal.
- An event for an unknown bet returns `ErrUnknownBet`.
- Safe for concurrent use by many goroutines.

## What you implement

- `State` enum: `StatePending, StateAccepted, StateSettled, StateRejected, StateCancelled` + `String()`.
- `Event` enum: `EventAccept, EventSettle, EventReject, EventCancel` + `String()`.
- `func NewBetMachine() *BetMachine`
- `func (m *BetMachine) Open(betID string) error`
- `func (m *BetMachine) Apply(betID string, e Event) (State, error)`
- `func (m *BetMachine) State(betID string) (State, bool)`

The enum types, their constants, and the error vars are given. You design all the
internals (the storage, the transition table, and the synchronisation strategy).

## The real challenge

- **Illegal transitions cost money.** The dangerous one is `Accept` after `Cancel`:
  a customer pulls their bet, then a delayed or replayed `Accept` lands and we
  "honour" a bet that was withdrawn — taking a position the customer never had.
  Reject illegal events and leave state untouched so a stray event can never
  corrupt the machine.
- **Double-firing pays out twice.** `Settle` is the payout. Under at-least-once
  delivery the broker *will* redeliver the same `Settle`. If two `Settle` events
  for one Accepted bet are both honoured, you pay the winnings twice. The real
  transition must happen exactly once; the redelivery must be a no-op.
- **The guard and the write must be one atomic critical section.** The naive split
  — read the state, decide it is legal, then separately write — is a
  time-of-check-to-time-of-use (TOCTOU) bug: two concurrent `Settle` events both
  read "Accepted", both decide it is legal, and both proceed → double fire. Put
  the validation *and* the state write under the same lock so only one event can
  observe the bet in a settleable state.
- **The idempotent no-op rule deduplicates redelivery.** Re-applying the same
  event that produced the current (terminal or target) state returns success
  without firing the side effect again. Be precise: same-event redelivery is the
  no-op; a different illegal event is `ErrIllegalTransition`.

## Run

There are no tests here — designing the tests is part of the exercise. Write your
own in this same package directory (`betmachine_test.go`), including a gated
concurrent test where many goroutines fire duplicate `Settle` events at one bet
and you assert the real transition happened exactly once. Then:

```
cd go-katas/practice && go test -race ./betmachine/
```

## Reference

Worked solution: `go-katas/solution/betmachine/`.

Extension: make idempotency **explicit** rather than inferring it from the target
state — attach a unique idempotency key (e.g. the broker's message id) to each
event and record processed keys, so a redelivered `Settle` is deduped by identity
even if an intervening event has since moved the state. Pair that with an
**append-only event log**: persist every applied event, derive state by replay,
and you gain a full audit trail ("why did this bet pay out?"), crash recovery, and
deterministic rebuilds.
