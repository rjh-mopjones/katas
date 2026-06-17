// Package settlement implements a settlement service that pays out winning bets
// when a market is settled, and exists to teach one thing: context propagation.
//
// When a market settles, the service calls a downstream payout dependency once
// per winning bet (in real life an HTTP/RPC call to a payments system). The
// caller of Settle owns a context.Context carrying a deadline and/or a cancel
// signal — "give up after 2s", or "the user navigated away, abandon this". The
// whole job of this kata is to thread that context into every downstream call so
// that when the caller gives up, the downstream work stops promptly.
//
// # The bug this kata is about
//
// The seductive mistake is to call the dependency with a fresh, detached
// context:
//
//	payout(context.Background(), bet)   // WRONG
//	payout(context.TODO(), bet)         // WRONG
//
// context.Background() has no deadline and is never cancelled. Passing it
// severs the link to the caller: the caller's timeout fires, the caller's cancel
// is invoked, Settle's own ctx is Done — and yet the payout call sails on,
// because the context it was handed knows nothing about any of that. The
// consequences are concrete, not theoretical:
//
//   - Wasted spend / load. The dependency keeps doing real work (and we keep
//     paying for it) for a settlement nobody is waiting for any more.
//   - Payouts on an abandoned settlement. Money can move for a settlement the
//     caller explicitly cancelled — a correctness and audit problem, not just a
//     performance one.
//   - Goroutine / connection leaks. In the concurrent variant, child goroutines
//     blocked on a detached call never observe cancellation and outlive the
//     request that spawned them.
//
// The rule: pass ctx down, never swallow it. The context you receive is the one
// you propagate. You may *narrow* it (add a tighter per-operation deadline with
// context.WithTimeout) but you never replace it with a detached one.
//
// # Propagation is a contract with two halves
//
// Propagation alone is necessary but not sufficient. Settle's responsibility is
// to *pass* ctx into payout; payout's responsibility is to *observe* it — to
// select on ctx.Done(), pass it to the HTTP request, and return ctx.Err()
// promptly when it fires. We do the first half here. A well-behaved PayoutFunc
// does the second. A PayoutFunc that ignores its ctx cannot be saved by us; the
// best we can do — and do below — is refuse to *start* further calls once ctx is
// already dead.
//
// # Checking ctx.Err() between iterations
//
// Even with a perfectly behaved payout, we check ctx.Err() at the top of every
// iteration *before* dispatching the next call. This gives prompt cancellation:
// if the context died while the previous payout was in flight, we abandon the
// remaining bets immediately instead of firing one more (now-pointless) downstream
// call and only then noticing. It is the cheap, synchronous guard that bounds how
// far past cancellation we can run to "at most one in-flight call".
//
// # Per-operation budget: context.WithTimeout + defer cancel
//
// When a single downstream call should get its own budget (independent of the
// caller's overall deadline), derive a child context:
//
//	callCtx, cancel := context.WithTimeout(ctx, perCallBudget)
//	defer cancel()
//	err := payout(callCtx, bet)
//
// WithTimeout returns a child that is cancelled when EITHER the parent is
// cancelled OR the budget elapses — propagation is preserved, just tightened. The
// defer cancel() is not optional: the timer and the child context hold resources
// (a goroutine + a entry in the parent's child list) that leak until cancel is
// called, even if the call returned early on success. "Always defer cancel" is the
// idiom; go vet's lostcancel check will flag you if you forget. The sequential
// Settle below keeps things simple and propagates the caller's ctx directly; the
// per-op-budget pattern is the natural next step once you add retries or a slow
// dependency.
//
// # Extension: bounded-concurrency settlement
//
// A market can have thousands of winning bets; settling them strictly in series
// is slow. The extension is a worker pool that settles up to N bets concurrently
// while STILL propagating ctx into every call and cancelling its siblings on the
// first failure — the errgroup pattern, but with stdlib only. The shape:
//
//	gctx, cancel := context.WithCancel(ctx)
//	defer cancel()
//	sem := make(chan struct{}, maxConcurrency) // bound in-flight work
//	var once sync.Once
//	var firstErr error
//	var wg sync.WaitGroup
//	for _, bet := range bets {
//	    if gctx.Err() != nil { break }        // stop dispatching once cancelled
//	    sem <- struct{}{}                      // acquire a slot
//	    wg.Add(1)
//	    go func(bet Bet) {
//	        defer wg.Done()
//	        defer func() { <-sem }()
//	        if err := payout(gctx, bet); err != nil {
//	            once.Do(func() { firstErr = err; cancel() }) // cancel siblings
//	        }
//	    }(bet)
//	}
//	wg.Wait()
//
// cancel() on first failure makes gctx Done, so the in-flight siblings (if their
// payout observes ctx) abort instead of grinding on for a settlement that has
// already failed — the concurrent analogue of the sequential "abandon remaining
// bets" behaviour. golang.org/x/sync/errgroup packages exactly this (with
// SetLimit for the bound); we reach for the stdlib here to keep the kata
// dependency-free.
package settlement

import (
	"context"
	"fmt"
)

// Bet is a single winning bet awaiting payout.
type Bet struct {
	ID    string
	Stake float64
}

// PayoutFunc is the injected downstream dependency: it pays out a single bet.
//
// In production this wraps an HTTP/RPC call to a payments system. It MUST receive
// the propagated context and is expected to honour it — selecting on ctx.Done(),
// attaching it to the outbound request, and returning ctx.Err() promptly when the
// caller cancels or the deadline passes. Settle propagates the context; a correct
// PayoutFunc observes it. That division of labour is the whole point.
type PayoutFunc func(ctx context.Context, bet Bet) error

// Settler settles markets by paying out winning bets through an injected
// PayoutFunc. It holds no mutable state and is safe to reuse across settlements.
type Settler struct {
	payout PayoutFunc
}

// NewSettler returns a Settler that pays out via the given PayoutFunc.
func NewSettler(payout PayoutFunc) *Settler {
	return &Settler{payout: payout}
}

// Settle pays out the bets in order, propagating ctx into every payout call.
//
// It returns the number of bets successfully settled and the first error
// encountered (nil if all succeeded). Behaviour:
//
//   - Before each call it checks ctx.Err(); if the context has been cancelled or
//     timed out, it stops immediately, abandons the remaining bets, and returns
//     the wrapped ctx error. This bounds overrun to at most one in-flight call.
//   - It passes the caller's ctx (never context.Background()) into each payout, so
//     a cancellation or deadline reaches the downstream dependency.
//   - On the first payout error it stops and returns that error; settled reflects
//     only the bets that completed successfully before it.
//
// The implementation is deliberately sequential: in-order, easy to reason about,
// and a clean baseline before the bounded-concurrency extension described in the
// package doc.
func (s *Settler) Settle(ctx context.Context, bets []Bet) (settled int, err error) {
	for _, bet := range bets {
		// Prompt cancellation: bail before dispatching the next call if the
		// caller has already given up.
		if err := ctx.Err(); err != nil {
			return settled, fmt.Errorf("settlement cancelled after %d/%d bets: %w", settled, len(bets), err)
		}

		// Propagate the caller's ctx downstream — never a detached background ctx.
		if err := s.payout(ctx, bet); err != nil {
			return settled, fmt.Errorf("payout for bet %s failed: %w", bet.ID, err)
		}

		settled++
	}
	return settled, nil
}
