// Package aggregator is a cold-drill scaffold for a low-latency market-data
// aggregator that you build up stage by stage. See SPEC.md for the staged
// requirements.
//
// Everything below is a STUB. The bodies are TODOs that only exist so the
// package compiles and `go vet` passes — there is no working logic here on
// purpose. You write all of it.
//
// Treat these types and signatures as a starting point for Stage 0, not a
// fixed contract: add, remove, rename, and reshape them freely as the later
// stages escalate (concurrency, staleness, subscriptions, backpressure,
// shutdown). The internal representation is entirely your decision.
package aggregator

import "time"

// PriceUpdate is a single back/lay quote from one venue for one market,
// stamped with the time it was observed.
type PriceUpdate struct {
	Venue  string
	Market string
	Back   float64
	Lay    float64
	Ts     time.Time
}

// Aggregator ingests a stream of PriceUpdates and serves the current best
// view per market. Stage 0 is single-threaded; you make it concurrent and
// grow it from there.
//
// The struct is intentionally empty: choosing the internal state is part of
// the exercise.
type Aggregator struct {
	// TODO: your state goes here.
}

// NewAggregator returns an Aggregator ready to accept updates.
func NewAggregator() *Aggregator {
	// TODO: implement.
	panic("TODO: implement")
}

// Apply ingests one price update.
func (a *Aggregator) Apply(u PriceUpdate) {
	// TODO: implement.
	panic("TODO: implement")
}

// Get returns the current best view for a market and whether one exists.
// The return type is yours to change as the requirements grow (e.g. a
// dedicated view type, staleness flags, etc.).
func (a *Aggregator) Get(market string) (PriceUpdate, bool) {
	// TODO: implement.
	panic("TODO: implement")
}
