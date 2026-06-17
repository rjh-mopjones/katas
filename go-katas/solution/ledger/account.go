package ledger

import "errors"

// Account is a single customer wallet.
//
// Balance is held in integer minor units (pence, cents) — never a float. Money
// math on float64 is wrong by construction: 0.1 + 0.2 != 0.3, rounding drifts,
// and a betting platform that loses a penny per million settlements loses real
// money and fails audit. Integers are exact; we choose the unit (minor units)
// and do all arithmetic there, formatting to a decimal only at the edges.
//
// Version is the optimistic-concurrency token: it is bumped on every change to
// the account. The pessimistic Ledger below does not need it to be correct (it
// holds the per-account lock across the whole read-modify-write), but it is the
// hook for the extension — a lock-free CAS retry that mirrors a SQL
// `UPDATE ... SET balance = ?, version = version + 1 WHERE id = ? AND version = ?`
// and retries on a zero-row update. Exposing it keeps the optimistic path a
// drop-in replacement.
type Account struct {
	ID      string
	Balance int64
	Version uint64
}

// Entry is one side of a double-entry posting in the audit trail.
//
// Amount is signed: positive is a credit (money in), negative is a debit (money
// out). Every business operation records balanced entries whose amounts sum to
// zero, so summing every Amount across the whole ledger is always zero — that is
// the conservation invariant a real ledger lives or dies by. A deposit/withdraw
// uses a single account plus an implicit external counter-entry; a transfer
// records a matched debit on the source and credit on the destination that
// cancel exactly.
type Entry struct {
	ID        string // unique posting id (the idempotency key that produced it)
	AccountID string // account this posting moves
	Amount    int64  // signed minor units: +credit, -debit
	Ref       string // human-readable reason, e.g. "deposit", "transfer:from", "transfer:to"
}

// Error vars returned by the Ledger. They are sentinel values so callers can
// branch with errors.Is rather than string-matching.
var (
	// ErrInsufficientFunds means a withdraw or transfer would overdraw the
	// source account. Wallets may not go negative.
	ErrInsufficientFunds = errors.New("ledger: insufficient funds")
	// ErrUnknownAccount means an operation referenced an account that was never
	// opened.
	ErrUnknownAccount = errors.New("ledger: unknown account")
	// ErrAccountExists means Open was called for an id that already exists.
	ErrAccountExists = errors.New("ledger: account already exists")
	// ErrSameAccount means a transfer named the same account as source and
	// destination — a no-op that almost always signals a caller bug, and which
	// would also deadlock a naive "lock from, then lock to" implementation.
	ErrSameAccount = errors.New("ledger: cannot transfer to the same account")
)
