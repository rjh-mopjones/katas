package venuefanin

import (
	"context"
)

// Quote is an immutable price quote from a single venue for a single market.
type Quote struct {
	Venue  string
	Market string
	Price  float64
}

// FanIn merges the given source channels onto a single output channel and
// returns the receive-only output for the consumer to range over.
func FanIn(ctx context.Context, bufferSize int, sources ...<-chan Quote) <-chan Quote {
	panic("TODO: implement")
}
