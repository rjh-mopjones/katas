package ledger

import "errors"

type Account struct {
	ID      string
	Balance int64
	Version uint64
}

type Entry struct {
	ID        string
	AccountID string
	Amount    int64
	Ref       string
}

var (
	ErrInsufficientFunds = errors.New("ledger: insufficient funds")
	ErrUnknownAccount    = errors.New("ledger: unknown account")
	ErrAccountExists     = errors.New("ledger: account already exists")
	ErrSameAccount       = errors.New("ledger: cannot transfer to the same account")
)
