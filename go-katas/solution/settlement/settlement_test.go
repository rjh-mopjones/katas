package settlement

import (
	"context"
	"errors"
	"testing"
)

func TestSettle_HappyPath_AllPaid(t *testing.T) {
	bets := []Bet{
		{ID: "b1", Stake: 10},
		{ID: "b2", Stake: 20},
		{ID: "b3", Stake: 30},
	}

	var gotCtxNonNil = true
	var calls int
	s := NewSettler(func(ctx context.Context, bet Bet) error {
		if ctx == nil {
			gotCtxNonNil = false
		}
		calls++
		return nil
	})

	settled, err := s.Settle(context.Background(), bets)
	if err != nil {
		t.Fatalf("Settle returned err = %v, want nil", err)
	}
	if settled != len(bets) {
		t.Fatalf("settled = %d, want %d", settled, len(bets))
	}
	if calls != len(bets) {
		t.Fatalf("payout called %d times, want %d", calls, len(bets))
	}
	if !gotCtxNonNil {
		t.Fatalf("payout received a nil ctx; Settle must always pass a real context")
	}
}

// TestSettle_StopsOnCancel proves prompt cancellation: the payout itself cancels
// the context after the first bet, and Settle must abandon the remaining bets and
// return the ctx error instead of dispatching another call.
func TestSettle_StopsOnCancel(t *testing.T) {
	bets := []Bet{
		{ID: "b1"}, {ID: "b2"}, {ID: "b3"}, {ID: "b4"},
	}

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	var calls int
	s := NewSettler(func(c context.Context, bet Bet) error {
		calls++
		// Caller gives up right after the first downstream call returns.
		cancel()
		return nil
	})

	settled, err := s.Settle(ctx, bets)
	if err == nil {
		t.Fatalf("Settle returned nil err, want a cancellation error")
	}
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("err = %v, want it to wrap context.Canceled", err)
	}
	if calls != 1 {
		t.Fatalf("payout called %d times, want exactly 1 (must stop after cancel)", calls)
	}
	if settled != 1 {
		t.Fatalf("settled = %d, want 1 (only the first bet completed before cancel)", settled)
	}
}

// TestSettle_PreCancelled is the boundary case: a context already dead before the
// first iteration must produce zero payout calls.
func TestSettle_PreCancelled_NoCalls(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel() // dead on arrival

	var calls int
	s := NewSettler(func(c context.Context, bet Bet) error {
		calls++
		return nil
	})

	settled, err := s.Settle(ctx, []Bet{{ID: "b1"}, {ID: "b2"}})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("err = %v, want it to wrap context.Canceled", err)
	}
	if calls != 0 {
		t.Fatalf("payout called %d times, want 0 (ctx dead before first call)", calls)
	}
	if settled != 0 {
		t.Fatalf("settled = %d, want 0", settled)
	}
}

// TestSettle_PropagatesContext proves the payout sees a context DERIVED from the
// caller's — not a detached context.Background(). We thread a value through the
// caller's ctx and assert every payout observes it.
func TestSettle_PropagatesContext(t *testing.T) {
	type ctxKey struct{}
	const want = "settlement-7f3a"

	ctx := context.WithValue(context.Background(), ctxKey{}, want)

	var observed int
	s := NewSettler(func(c context.Context, bet Bet) error {
		got, ok := c.Value(ctxKey{}).(string)
		if !ok || got != want {
			t.Errorf("bet %s: payout ctx value = %q (ok=%v), want %q — ctx not propagated",
				bet.ID, got, ok, want)
		}
		observed++
		return nil
	})

	bets := []Bet{{ID: "b1"}, {ID: "b2"}, {ID: "b3"}}
	settled, err := s.Settle(ctx, bets)
	if err != nil {
		t.Fatalf("Settle returned err = %v, want nil", err)
	}
	if settled != len(bets) || observed != len(bets) {
		t.Fatalf("settled=%d observed=%d, want %d for both", settled, observed, len(bets))
	}
}

// TestSettle_ReturnsFirstError checks that a mid-stream payout failure stops
// settlement, surfaces that error, and reports only the successful count.
func TestSettle_ReturnsFirstError(t *testing.T) {
	bets := []Bet{{ID: "b1"}, {ID: "b2"}, {ID: "b3"}, {ID: "b4"}}

	boom := errors.New("payments down")
	var calls int
	s := NewSettler(func(c context.Context, bet Bet) error {
		calls++
		if bet.ID == "b3" {
			return boom
		}
		return nil
	})

	settled, err := s.Settle(context.Background(), bets)
	if !errors.Is(err, boom) {
		t.Fatalf("err = %v, want it to wrap %v", err, boom)
	}
	if settled != 2 {
		t.Fatalf("settled = %d, want 2 (b1, b2 before the b3 failure)", settled)
	}
	if calls != 3 {
		t.Fatalf("payout called %d times, want 3 (stops after the failing b3)", calls)
	}
}
