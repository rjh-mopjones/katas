// Package ledger implements the money store for a betting platform: customer
// wallets backed by a double-entry ledger, safe under concurrent access.
//
// This is the kata where correctness is the whole point. Four hazards, each a
// classic interview topic, all live in the same small type:
//
//   - Lost updates. Crediting an account is a read-modify-write: read balance,
//     add amount, write balance back. If two goroutines interleave their RMW on
//     the same account, one update is silently lost (both read 100, both write
//     150, final is 150 not 200). The fix is to hold the account's lock across
//     the ENTIRE read-modify-write, not just the read or just the write.
//   - Idempotency. Messages are delivered at-least-once: the same "credit
//     winnings" or "settle bet" command can arrive twice (retries, redeliveries).
//     A non-idempotent ledger pays out twice — a direct money loss. We dedupe by
//     an idempotency key: the first application of a key takes effect and its
//     outcome is remembered; any later application of the same key is a no-op
//     that returns the original outcome.
//   - Deadlock. A transfer must lock two accounts. If goroutine 1 transfers A→B
//     (locks A then B) while goroutine 2 transfers B→A (locks B then A), they can
//     each hold one lock and wait forever for the other — a deadlock from
//     inconsistent lock ordering. The fix is a TOTAL ORDER on lock acquisition:
//     always lock the lower account id first, regardless of transfer direction.
//     With a consistent order no cycle of "waits-for" can form, so no deadlock.
//   - Double-entry conservation. Money is never created or destroyed inside the
//     ledger; it only moves. Every operation records balanced postings (a
//     transfer is a -X debit and a +X credit), so the sum of every entry's
//     amount is always zero. CheckInvariant asserts it.
//
// # Why the idempotency check and the mutation must be atomic together
//
// The subtle bug is a TOCTOU (time-of-check to time-of-use) race on the seen-keys
// set. If we "check seen?" under one lock, release it, then "apply + mark seen"
// under another, two duplicate deliveries of the same key can both pass the
// "seen?" check before either marks it — and both apply. The money moves twice.
// So the dedupe lookup, the balance mutation, and the recording of the key's
// outcome must all happen inside one critical section that no duplicate can
// slip between. We hold the account's lock across the whole sequence.
//
// # Concurrency model
//
//   - The ledger map (id → *account) is guarded by its own RWMutex, used ONLY to
//     find or create accounts. We never hold it across a balance mutation, so
//     account operations on different accounts run fully in parallel.
//   - Each account carries its own sync.Mutex. A single-account operation
//     (deposit/withdraw) locks just that account for its whole RMW. A transfer
//     locks BOTH accounts, in sorted-id order, for the whole atomic move.
//   - The idempotency record lives ON the account (per-account seen-keys map)
//     and is read+written under the same account lock that guards the balance,
//     so the check and the mutation are inseparable. A transfer's key is recorded
//     under the lock pair, so a duplicate transfer is caught no matter which of
//     the two accounts a concurrent duplicate reaches first.
//
// # Persistence-layer analogues
//
// In a database the same RMW-under-lock is expressed two ways. Pessimistic:
// `SELECT balance FROM accounts WHERE id = ? FOR UPDATE` takes a row lock for the
// transaction, the exact analogue of our per-account Mutex; ordering the locked
// rows (e.g. `ORDER BY id` in the SELECT, or always touching the lower id first)
// is the same deadlock fix. Optimistic: add a `version` column and do
// `UPDATE accounts SET balance = ?, version = version + 1 WHERE id = ? AND
// version = ?`; if it updates zero rows someone else moved first, so re-read and
// retry (bounded). The optimistic CAS-retry is the kata extension; the Version
// field on Account is the hook for it.
package ledger

import (
	"fmt"
	"sort"
	"sync"
)

// outcome records the result of applying an idempotency key, so a duplicate
// delivery can be answered identically without re-applying the effect.
type outcome struct {
	err error
}

// account is the internal, lock-bearing wallet. The exported Account is a
// snapshot value; this is the live, mutable cell.
type account struct {
	mu      sync.Mutex
	id      string
	balance int64
	version uint64
	seen    map[string]outcome // idempotency keys applied to this account
	entries []Entry            // this account's slice of the audit trail
}

// Ledger is a concurrency-safe set of customer wallets backed by a double-entry
// audit trail. The zero value is not usable; construct one with New.
type Ledger struct {
	mu       sync.RWMutex
	accounts map[string]*account
}

// New returns an empty, ready-to-use Ledger.
func New() *Ledger {
	return &Ledger{accounts: make(map[string]*account)}
}

// Open creates a new account with the given starting balance.
//
// It takes the ledger write lock only long enough to insert the account, never
// across a balance mutation. Returns ErrAccountExists if id is already open.
func (l *Ledger) Open(id string, initial int64) error {
	l.mu.Lock()
	defer l.mu.Unlock()
	if _, ok := l.accounts[id]; ok {
		return fmt.Errorf("open %q: %w", id, ErrAccountExists)
	}
	a := &account{
		id:      id,
		balance: initial,
		seen:    make(map[string]outcome),
	}
	if initial != 0 {
		// Opening with a starting balance is itself money entering the ledger,
		// balanced by an implicit external counter-entry. We record the account
		// side; CheckInvariant accounts for the external leg (see below).
		a.entries = append(a.entries, Entry{
			ID:        "open:" + id,
			AccountID: id,
			Amount:    initial,
			Ref:       "open",
		})
	}
	l.accounts[id] = a
	return nil
}

// find returns the live account for id, or ErrUnknownAccount. Read-locks the
// ledger map only.
func (l *Ledger) find(id string) (*account, error) {
	l.mu.RLock()
	defer l.mu.RUnlock()
	a, ok := l.accounts[id]
	if !ok {
		return nil, fmt.Errorf("account %q: %w", id, ErrUnknownAccount)
	}
	return a, nil
}

// Balance returns the current balance of id in minor units.
func (l *Ledger) Balance(id string) (int64, error) {
	a, err := l.find(id)
	if err != nil {
		return 0, err
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.balance, nil
}

// Deposit credits amount (in minor units) to id, idempotent by idemKey.
//
// The whole read-modify-write plus the idempotency check happens under the
// account lock, so neither a lost update nor a duplicate credit is possible. A
// repeated idemKey returns the original outcome and moves no money.
func (l *Ledger) Deposit(idemKey, id string, amount int64) error {
	a, err := l.find(id)
	if err != nil {
		return err
	}
	a.mu.Lock()
	defer a.mu.Unlock()

	if o, ok := a.seen[idemKey]; ok {
		return o.err // duplicate delivery: replay the original outcome, no effect
	}

	a.balance += amount
	a.version++
	a.entries = append(a.entries, Entry{
		ID:        idemKey,
		AccountID: id,
		Amount:    amount,
		Ref:       "deposit",
	})
	a.seen[idemKey] = outcome{err: nil}
	return nil
}

// Withdraw debits amount (in minor units) from id, idempotent by idemKey.
//
// Rejects an overdraw with ErrInsufficientFunds. The funds check and the
// mutation are one critical section, so a passing check cannot be invalidated by
// a concurrent withdraw before the debit lands.
func (l *Ledger) Withdraw(idemKey, id string, amount int64) error {
	a, err := l.find(id)
	if err != nil {
		return err
	}
	a.mu.Lock()
	defer a.mu.Unlock()

	if o, ok := a.seen[idemKey]; ok {
		return o.err
	}

	if a.balance < amount {
		// Record the rejection against the key so a retry of the same command is
		// rejected identically rather than racing a now-funded account.
		o := outcome{err: fmt.Errorf("withdraw %d from %q: %w", amount, id, ErrInsufficientFunds)}
		a.seen[idemKey] = o
		return o.err
	}

	a.balance -= amount
	a.version++
	a.entries = append(a.entries, Entry{
		ID:        idemKey,
		AccountID: id,
		Amount:    -amount,
		Ref:       "withdraw",
	})
	a.seen[idemKey] = outcome{err: nil}
	return nil
}

// Transfer atomically moves amount (in minor units) from one account to another,
// idempotent by idemKey.
//
// It rejects a same-account transfer (ErrSameAccount) and an overdraw of the
// source (ErrInsufficientFunds). It is deadlock-free under concurrent opposing
// transfers (A→B while B→A) because it always acquires the two account locks in
// a total order — sorted by id — so no waits-for cycle can form. The debit and
// the matching credit are recorded as balanced double-entry postings.
func (l *Ledger) Transfer(idemKey, from, to string, amount int64) error {
	if from == to {
		return fmt.Errorf("transfer %q->%q: %w", from, to, ErrSameAccount)
	}

	src, err := l.find(from)
	if err != nil {
		return err
	}
	dst, err := l.find(to)
	if err != nil {
		return err
	}

	// Total-order lock acquisition: always lock the lower id first, regardless of
	// transfer direction. This is the deadlock fix — see package doc.
	first, second := src, dst
	if first.id > second.id {
		first, second = second, first
	}
	first.mu.Lock()
	defer first.mu.Unlock()
	second.mu.Lock()
	defer second.mu.Unlock()

	// Idempotency for a transfer is recorded on the source account; check it once
	// under the held lock pair so a duplicate delivery (reaching either account)
	// is caught and replayed without moving money twice.
	if o, ok := src.seen[idemKey]; ok {
		return o.err
	}

	if src.balance < amount {
		o := outcome{err: fmt.Errorf("transfer %d %q->%q: %w", amount, from, to, ErrInsufficientFunds)}
		src.seen[idemKey] = o
		return o.err
	}

	src.balance -= amount
	dst.balance += amount
	src.version++
	dst.version++

	// Balanced double-entry posting: a -amount debit on the source and a +amount
	// credit on the destination. Their sum is zero, preserving conservation.
	src.entries = append(src.entries, Entry{
		ID:        idemKey,
		AccountID: from,
		Amount:    -amount,
		Ref:       "transfer:from",
	})
	dst.entries = append(dst.entries, Entry{
		ID:        idemKey,
		AccountID: to,
		Amount:    amount,
		Ref:       "transfer:to",
	})

	src.seen[idemKey] = outcome{err: nil}
	return nil
}

// Entries returns a copy of the audit trail for id (the postings that touched
// this account), in the order they were applied.
//
// The returned slice is a fresh allocation the caller owns; it never aliases the
// internal slice, so the caller cannot mutate ledger state by accident.
func (l *Ledger) Entries(id string) []Entry {
	a, err := l.find(id)
	if err != nil {
		return nil
	}
	a.mu.Lock()
	defer a.mu.Unlock()
	out := make([]Entry, len(a.entries))
	copy(out, a.entries)
	return out
}

// CheckInvariant asserts the double-entry conservation invariant: across the
// whole ledger, the postings reconcile.
//
// Two facts are checked, both of which hold for a correct ledger:
//
//   - Per-account reconciliation: the sum of an account's own entry amounts
//     equals its current balance. (Every change to a balance recorded a matching
//     entry of equal signed amount.)
//   - Global conservation of internal movements: transfers move money between
//     accounts without creating it, so the sum of all transfer postings across
//     the ledger is exactly zero. Deposits, withdrawals and opening balances are
//     money crossing the ledger boundary (the external/world leg), so they are
//     excluded from the internal-conservation sum.
//
// Returns a descriptive error if either check fails. It snapshots accounts under
// the ledger lock, then locks each account in turn — it does not freeze the whole
// ledger, so it asserts a per-account-consistent view rather than one global
// instant. That is sufficient: the invariant is structural, not timing-dependent.
func (l *Ledger) CheckInvariant() error {
	l.mu.RLock()
	accounts := make([]*account, 0, len(l.accounts))
	for _, a := range l.accounts {
		accounts = append(accounts, a)
	}
	l.mu.RUnlock()

	// Stable order so error messages are deterministic.
	sort.Slice(accounts, func(i, j int) bool { return accounts[i].id < accounts[j].id })

	var internal int64 // sum of transfer postings only — must net to zero
	for _, a := range accounts {
		a.mu.Lock()
		var sum int64
		for _, e := range a.entries {
			sum += e.Amount
			if e.Ref == "transfer:from" || e.Ref == "transfer:to" {
				internal += e.Amount
			}
		}
		bal := a.balance
		a.mu.Unlock()

		if sum != bal {
			return fmt.Errorf("account %q: entries sum to %d but balance is %d", a.id, sum, bal)
		}
	}

	if internal != 0 {
		return fmt.Errorf("transfer postings do not conserve: net %d, want 0", internal)
	}
	return nil
}
