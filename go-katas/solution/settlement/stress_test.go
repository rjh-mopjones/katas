package settlement

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
)

// TestSettle_RaceStress runs many concurrent Settle calls, each with its own
// []Bet and its own context. Half the calls cancel their context deterministically
// mid-flight (a payout cancels after a fixed bet), the other half run to
// completion. It asserts: cancelled runs stop early and return the ctx error;
// clean runs settle every bet; the PayoutFunc always sees a non-nil ctx derived
// from the caller; no panic, -race clean.
func TestSettle_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	const (
		callers     = 64
		betsPerCall = 50
		cancelAtBet = 10 // index at which cancelling callers give up
		callerLoops = 30
	)

	// The Settler is stateless and reused across all callers concurrently. The
	// PayoutFunc reads the per-call signal stashed on the context to decide whether
	// to cancel; it never mutates shared state except the atomic counters.
	var sawNilCtx atomic.Int64
	var payouts atomic.Int64

	type callKey struct{}

	s := NewSettler(func(ctx context.Context, bet Bet) error {
		if ctx == nil {
			sawNilCtx.Add(1)
			return nil
		}
		payouts.Add(1)
		// A cancelling caller stashes its CancelFunc + trigger index on the ctx;
		// when this payout is the trigger bet, it cancels the caller's context,
		// modelling "the caller gave up mid-settlement". Bets carry their index in
		// Stake (set by the caller below), so no SUT field is needed.
		if c, ok := ctx.Value(callKey{}).(*cancelTrigger); ok {
			if int(bet.Stake) == c.trigger {
				c.cancel()
			}
		}
		// Honour the context: a well-behaved payout returns ctx.Err() promptly.
		if err := ctx.Err(); err != nil {
			return err
		}
		return nil
	})

	start := make(chan struct{})
	var wg sync.WaitGroup
	wg.Add(callers)

	var failures atomic.Int64

	for c := 0; c < callers; c++ {
		cancelling := c%2 == 0
		go func(cancelling bool) {
			defer wg.Done()
			<-start
			for loop := 0; loop < callerLoops; loop++ {
				bets := make([]Bet, betsPerCall)
				for i := range bets {
					bets[i] = Bet{ID: "b", Stake: float64(i)} // Stake encodes the index.
				}

				ctx, cancel := context.WithCancel(context.Background())
				if cancelling {
					ct := &cancelTrigger{cancel: cancel, trigger: cancelAtBet}
					ctx = context.WithValue(ctx, callKey{}, ct)
				}

				settled, err := s.Settle(ctx, bets)

				if cancelling {
					// Must stop early and surface the ctx error.
					if err == nil {
						failures.Add(1)
					} else if !errors.Is(err, context.Canceled) {
						failures.Add(1)
					}
					if settled >= len(bets) {
						failures.Add(1) // did not stop early
					}
				} else {
					if err != nil {
						failures.Add(1)
					}
					if settled != len(bets) {
						failures.Add(1)
					}
				}
				cancel()
			}
		}(cancelling)
	}

	close(start)
	wg.Wait()

	if n := sawNilCtx.Load(); n != 0 {
		t.Fatalf("PayoutFunc received a nil ctx %d times; Settle must always pass a real context", n)
	}
	if n := failures.Load(); n != 0 {
		t.Fatalf("%d Settle invariant violations under stress (early-stop / all-settled / ctx error)", n)
	}
	if payouts.Load() == 0 {
		t.Fatalf("no payouts recorded; stress did nothing")
	}
}

// cancelTrigger carries a caller's CancelFunc and the bet index at which a
// cancelling caller gives up, so the shared PayoutFunc can drive deterministic
// mid-flight cancellation without any sleeps.
type cancelTrigger struct {
	cancel  context.CancelFunc
	trigger int
}
