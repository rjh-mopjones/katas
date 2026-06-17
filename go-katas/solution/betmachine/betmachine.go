// Package betmachine implements a concurrency-safe, transition-guarded state
// machine for the lifecycle of a bet (or, equivalently, any order whose state
// transitions trigger money-moving side effects).
//
// A bet flows through a small set of states:
//
//	Pending  --Accept--> Accepted --Settle--> Settled   (terminal)
//	Pending  --Reject--> Rejected                        (terminal)
//	Pending  --Cancel--> Cancelled                       (terminal)
//	Accepted --Cancel--> Cancelled                       (terminal)
//
// The interesting part is not the table — it is that lifecycle events arrive
// over an unreliable transport with at-least-once delivery: an event can be
// DUPLICATED, and concurrent events for the same bet can race. Each transition
// stands in for a real side effect (Settle pays out, Reject releases the stake,
// Cancel voids and refunds), so getting the guard wrong costs money.
//
// # The two failure modes this type guards against
//
//   - Illegal transitions. Honouring an event that the current state does not
//     permit means acting on a bet that has already left that part of its life.
//     The dangerous one is Accept after Cancel: a customer pulls their bet, then
//     a delayed or replayed Accept event lands and we "honour" a bet that was
//     withdrawn — we have taken a position the customer never had. Illegal
//     transitions are rejected with ErrIllegalTransition and leave state
//     UNCHANGED, so a stray event can never corrupt the machine.
//
//   - Double-firing. Settle is the side effect that pays out. If two Settle
//     events for the same Accepted bet are both honoured, we pay the winnings
//     twice. Under at-least-once delivery this is the normal case, not an edge
//     case — the broker WILL redeliver. The machine must perform the real
//     transition exactly once and treat the redelivery as a no-op success.
//
// # Why the check-and-set must be atomic under one lock
//
// The naive split — read the state, decide it is legal, then (separately) write
// the new state — is a time-of-check-to-time-of-use (TOCTOU) bug. Two concurrent
// Settle events for the same Accepted bet can BOTH read "Accepted", BOTH conclude
// the transition is legal, and BOTH proceed: a double payout, even though each
// call individually looked correct. The validation AND the state write therefore
// happen inside a single critical section under the same mutex. Only one event
// can observe the bet in a state from which Settle is a real transition; every
// other concurrent or duplicate Settle observes the already-Settled state and is
// short-circuited to an idempotent no-op. The lock is what collapses "check" and
// "set" into one indivisible step, which is exactly what prevents the double
// fire.
//
// # The idempotency rule for duplicate delivery
//
// Applying an event that targets the state the bet is ALREADY in is a no-op
// success: it returns the current state with a nil error and does NOT re-run the
// side effect. Re-Settling a Settled bet, re-Rejecting a Rejected bet, or
// re-Cancelling a Cancelled bet are all benign redeliveries. This is the dedup
// for at-least-once transport. Be precise about the distinction: re-applying the
// SAME event that produced the current (terminal or target) state is the
// idempotent no-op; a DIFFERENT event against a terminal state is still an
// illegal transition and returns ErrIllegalTransition (e.g. Accept on a Settled
// bet is illegal, not a no-op).
//
// # Extension
//
// Production systems make idempotency explicit rather than inferring it from the
// target state: attach a unique idempotency key to each event (e.g. the broker's
// message id) and record processed keys, so a redelivered Settle is deduped by
// identity even if an intervening event has since moved the state. Pair that with
// an append-only event log: persist every applied event, derive state by replay,
// and you get a full audit trail ("why did this bet pay out?"), crash recovery,
// and the ability to rebuild the machine deterministically.
package betmachine

import (
	"errors"
	"sync"
)

// State is the lifecycle position of a bet.
type State int

// The bet lifecycle states. Settled, Rejected and Cancelled are terminal.
const (
	StatePending State = iota
	StateAccepted
	StateSettled
	StateRejected
	StateCancelled
)

// String returns the human-readable name of the state.
func (s State) String() string {
	switch s {
	case StatePending:
		return "Pending"
	case StateAccepted:
		return "Accepted"
	case StateSettled:
		return "Settled"
	case StateRejected:
		return "Rejected"
	case StateCancelled:
		return "Cancelled"
	default:
		return "Unknown"
	}
}

// Event is a lifecycle command applied to a bet.
type Event int

// The lifecycle events. Each drives the bet toward a particular target state.
const (
	EventAccept Event = iota
	EventSettle
	EventReject
	EventCancel
)

// String returns the human-readable name of the event.
func (e Event) String() string {
	switch e {
	case EventAccept:
		return "Accept"
	case EventSettle:
		return "Settle"
	case EventReject:
		return "Reject"
	case EventCancel:
		return "Cancel"
	default:
		return "Unknown"
	}
}

// ErrUnknownBet is returned when an event targets a bet that was never Opened.
var ErrUnknownBet = errors.New("betmachine: unknown bet")

// ErrIllegalTransition is returned when an event is not permitted from the bet's
// current state. The bet's state is left unchanged.
var ErrIllegalTransition = errors.New("betmachine: illegal transition")

// target maps each event to the state it transitions a bet INTO. It is used both
// to validate transitions (via the allowed table) and to recognise idempotent
// redeliveries: an event whose target equals the current state is a no-op.
var target = map[Event]State{
	EventAccept: StateAccepted,
	EventSettle: StateSettled,
	EventReject: StateRejected,
	EventCancel: StateCancelled,
}

// allowed lists, for each event, the set of states from which it is a legal
// real transition. Terminal states appear in no entry, so no event can move a
// bet out of a terminal state.
var allowed = map[Event]map[State]bool{
	EventAccept: {StatePending: true},
	EventSettle: {StateAccepted: true},
	EventReject: {StatePending: true},
	EventCancel: {StatePending: true, StateAccepted: true},
}

// BetMachine tracks the lifecycle state of many bets and applies guarded,
// idempotent transitions to them. It is safe for concurrent use by multiple
// goroutines. The zero value is not usable; construct one with NewBetMachine.
type BetMachine struct {
	mu   sync.Mutex
	bets map[string]State
}

// NewBetMachine returns an empty, ready-to-use BetMachine.
func NewBetMachine() *BetMachine {
	return &BetMachine{bets: make(map[string]State)}
}

// Open registers a new bet in StatePending. It returns an error if a bet with
// the same id already exists, so a duplicate Open cannot reset a bet that has
// already progressed.
func (m *BetMachine) Open(betID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, exists := m.bets[betID]; exists {
		return errors.New("betmachine: bet already exists: " + betID)
	}
	m.bets[betID] = StatePending
	return nil
}

// Apply applies event e to the bet identified by betID and returns the bet's
// state after the call.
//
// All validation and the state write happen inside one critical section, which
// is what makes the machine safe under concurrent at-least-once delivery: the
// check and the set cannot be split by another goroutine (no TOCTOU double fire).
//
// Behaviour:
//   - Unknown bet: returns (StatePending, ErrUnknownBet).
//   - Idempotent no-op: if the event targets the state the bet is already in
//     (e.g. Settle on an already-Settled bet), returns the current state and nil
//     WITHOUT re-running the side effect — this is the dedup for redelivered
//     events.
//   - Legal transition: moves the bet to the target state and returns it with nil.
//   - Anything else (illegal transition, including a different event against a
//     terminal state): returns the unchanged current state and
//     ErrIllegalTransition.
func (m *BetMachine) Apply(betID string, e Event) (State, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	cur, ok := m.bets[betID]
	if !ok {
		return StatePending, ErrUnknownBet
	}

	// Idempotent redelivery: the event targets the state we are already in, so
	// the real transition (and its side effect) has already happened. No-op.
	if target[e] == cur {
		return cur, nil
	}

	// Real transition: only legal if the current state is in the event's allowed
	// set. This read-decide-write is indivisible because we hold the lock.
	if allowed[e][cur] {
		next := target[e]
		m.bets[betID] = next
		return next, nil
	}

	// Not the idempotent target and not a legal source: illegal. State unchanged.
	return cur, ErrIllegalTransition
}

// State returns the current state of the bet and whether the bet is known.
func (m *BetMachine) State(betID string) (State, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	s, ok := m.bets[betID]
	return s, ok
}
