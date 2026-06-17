package settlepipeline

import (
	"context"
)

// Bet is a single bet to be settled when its market resolves.
type Bet struct {
	ID    string
	Stake int64
}

// Validated is a Bet that has passed validation.
type Validated struct {
	Bet Bet
}

// Reserved is a Validated bet whose payout funds have been reserved.
type Reserved struct {
	Bet      Bet
	Reserved int64
}

// Settled is a Reserved bet that has been settled with a final payout.
type Settled struct {
	Bet    Bet
	Payout int64
}

// Notified is the terminal result: a Settled bet whose customer has been
// notified. The pipeline collects the IDs of these to report success.
type Notified struct {
	Bet Bet
}

// StageFuncs bundles the four per-stage functions so they can be injected — in
// production they hit real services; in tests they force errors, slowness, or
// instrument concurrency. Each takes the upstream value and the (derived)
// pipeline context, returning the next stage's value or a fatal error.
type StageFuncs struct {
	Validate func(context.Context, Bet) (Validated, error)
	Reserve  func(context.Context, Validated) (Reserved, error)
	Settle   func(context.Context, Reserved) (Settled, error)
	Notify   func(context.Context, Settled) (Notified, error)
}

// Pipeline wires the four settlement stages over bounded channels with a
// bounded worker pool per stage.
type Pipeline struct{}

// New builds a Pipeline from the four stage functions and per-stage worker and
// buffer sizes (indexed validate, reserve, settle, notify). Non-positive sizes
// default to 1.
func New(fns StageFuncs, workers, buffers [4]int) *Pipeline {
	panic("TODO: implement")
}

// Run drives every bet through validate → reserve → settle → notify and returns
// the IDs of the successfully notified bets, or the first fatal error.
func (p *Pipeline) Run(ctx context.Context, bets []Bet) ([]string, error) {
	panic("TODO: implement")
}
