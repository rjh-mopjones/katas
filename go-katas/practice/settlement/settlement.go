package settlement

import (
	"context"
)

// Bet is a single winning bet awaiting payout.
type Bet struct {
	ID    string
	Stake float64
}

// PayoutFunc is the injected downstream dependency: it pays out a single bet.
// It MUST receive the propagated context.
type PayoutFunc func(ctx context.Context, bet Bet) error

// Settler settles markets by paying out winning bets through an injected PayoutFunc.
type Settler struct{}

// NewSettler returns a Settler that pays out via the given PayoutFunc.
func NewSettler(payout PayoutFunc) *Settler {
	panic("TODO: implement")
}

// Settle pays out the bets in order, propagating ctx into every payout call. It
// returns the number of bets successfully settled and the first error encountered.
func (s *Settler) Settle(ctx context.Context, bets []Bet) (settled int, err error) {
	panic("TODO: implement")
}
