# Ledger / Wallet

> The money store for the betting platform: customer wallets backed by a double-entry ledger, correct under concurrent access.

## The problem

This is the wallet service. Customers have balances; money is deposited, withdrawn,
and transferred between accounts. It is the part of the system where a single
wrong penny is a real loss and an audit failure, so correctness — not throughput —
is the whole point. Four hazards all live in this one small type:

- **Lost updates.** Crediting an account is a read-modify-write (read balance, add,
  write back). Two goroutines interleaving their RMW on the same account silently
  lose one update.
- **At-least-once delivery.** The same command ("credit winnings", "settle bet")
  can arrive twice. A non-idempotent ledger pays out twice.
- **Deadlock.** A transfer locks two accounts. `A→B` (locks A then B) racing `B→A`
  (locks B then A) can hang forever.
- **Conservation.** Money is never created or destroyed inside the ledger; it only
  moves. The books must balance.

Money is integer **minor units** (pence/cents) throughout — never `float64`.

## Requirements

- `Open(id, initial)` creates an account; a duplicate id is `ErrAccountExists`.
- `Balance(id)` returns current minor units; an unknown id is `ErrUnknownAccount`.
- `Deposit` / `Withdraw` are **idempotent by `idemKey`**: a repeated key is a no-op
  that returns the original outcome. `Withdraw` rejects overdraw with
  `ErrInsufficientFunds`.
- `Transfer` atomically moves money, idempotent by key, rejecting overdraw
  (`ErrInsufficientFunds`) and same-account (`ErrSameAccount`). It must be
  **deadlock-free** under concurrent opposing transfers.
- Every operation records balanced **double-entry** postings; `CheckInvariant`
  asserts the books reconcile.
- All of the above must hold under concurrent access and be `-race` clean.

## What you implement

The exported `Ledger` API:

- `func New() *Ledger`
- `func (l *Ledger) Open(id string, initial int64) error`
- `func (l *Ledger) Balance(id string) (int64, error)`
- `func (l *Ledger) Deposit(idemKey, id string, amount int64) error`
- `func (l *Ledger) Withdraw(idemKey, id string, amount int64) error`
- `func (l *Ledger) Transfer(idemKey, from, to string, amount int64) error`
- `func (l *Ledger) Entries(id string) []Entry`
- `func (l *Ledger) CheckInvariant() error`

The `Account` and `Entry` structs and the `Err*` sentinels are given. You design
all internals — the storage, the locking strategy, the idempotency record, and the
audit trail.

## Stages

A ~60-minute path that escalates scope; get each stage's own tests green before
moving on.

1. **Accounts + money.** `New`, `Open`, `Balance`, `Deposit`, `Withdraw`. Hold the
   balance in **integer minor units**. Record a balanced double-entry posting on
   every change and reconcile it in `CheckInvariant`. Reject overdraw.
2. **Idempotency.** Add an `idemKey` dedupe so a repeated key is a no-op returning
   the original outcome — for deposit, withdraw, *and* the rejection of an overdraw.
3. **Transfers + deadlock-free ordering.** `Transfer`: atomic move of two accounts.
   Acquire the two account locks in a **total order** (sorted by id) so opposing
   transfers can't deadlock. Reject same-account and overdraw. Record the matched
   debit/credit postings.
4. **Prove it under concurrency.** Write the gated concurrent tests: no-lost-update
   (G goroutines each deposit +1 N times → balance == G*N), deadlock-free opposing
   transfers (A→B and B→A, completes without hanging, A+B conserved), and a
   randomized workload where `CheckInvariant` still holds. Run under `-race`.

## The real challenge

- **The lost-update RMW must be *fully* locked.** Locking only the read or only
  the write still loses updates. Hold the account's lock across the entire
  read-modify-write.
- **Idempotency is a TOCTOU trap.** If you "check seen?" under one lock, release,
  then "apply + mark seen" under another, two duplicate deliveries can both pass
  the check before either marks it — and both apply. The money moves twice. The
  dedupe lookup, the mutation, and recording the key's outcome must be **one
  critical section**. (The money angle: a duplicated "settle" must not pay twice.)
- **Deadlock comes from inconsistent lock order.** "Lock `from`, then `to`" lets
  `A→B` and `B→A` each hold one lock and wait for the other. Always lock the
  lower id first — with a consistent total order no waits-for cycle can form.
- **Double-entry conservation.** Every movement is balanced; transfer postings
  across the whole ledger net to zero, and each account's entries sum to its
  balance.
- **Persistence-layer analogues.** Pessimistic: `SELECT ... FOR UPDATE` is the row
  lock equivalent of the per-account mutex (and ordering the locked rows is the
  same deadlock fix). Optimistic: a `version` column with
  `UPDATE ... SET balance = ?, version = version + 1 WHERE id = ? AND version = ?`,
  retrying on a zero-row update — see the extension.

## Run

There are no tests here — designing them is part of the exercise. Write your own
in this package directory (`ledger_test.go`): the happy paths, the rejections, the
idempotency cases, and the gated concurrency tests (use a `close(start)` gate +
`sync.WaitGroup`; guard the deadlock test against a hang with a `done` channel +
`select`/`time.After`, **not** a sleep). Then:

```
cd go-katas/practice && go test -race ./ledger/
```

## Reference

Worked solution: `go-katas/solution/ledger/`.

Extension: replace the pessimistic per-account locks with an **optimistic
`Version` CAS** — read the account and its version, compute the new balance, then
commit only if the version is unchanged (bumping it), retrying with bounded
attempts on conflict. This mirrors a SQL `UPDATE ... WHERE version = ?` and is the
`Version` field's reason for existing.
