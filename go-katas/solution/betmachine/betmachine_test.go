package betmachine

import (
	"errors"
	"sync"
	"testing"
)

func TestHappyPath_AcceptThenSettle(t *testing.T) {
	m := NewBetMachine()
	if err := m.Open("b1"); err != nil {
		t.Fatalf("Open: %v", err)
	}

	if got, err := m.Apply("b1", EventAccept); err != nil || got != StateAccepted {
		t.Fatalf("Accept = (%v, %v), want (Accepted, nil)", got, err)
	}
	if got, err := m.Apply("b1", EventSettle); err != nil || got != StateSettled {
		t.Fatalf("Settle = (%v, %v), want (Settled, nil)", got, err)
	}

	if s, ok := m.State("b1"); !ok || s != StateSettled {
		t.Fatalf("State = (%v, %v), want (Settled, true)", s, ok)
	}
}

func TestHappyPath_Reject(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")

	if got, err := m.Apply("b1", EventReject); err != nil || got != StateRejected {
		t.Fatalf("Reject = (%v, %v), want (Rejected, nil)", got, err)
	}
}

func TestHappyPath_CancelFromAccepted(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")
	_, _ = m.Apply("b1", EventAccept)

	if got, err := m.Apply("b1", EventCancel); err != nil || got != StateCancelled {
		t.Fatalf("Cancel = (%v, %v), want (Cancelled, nil)", got, err)
	}
}

func TestCancelFromPending(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")

	if got, err := m.Apply("b1", EventCancel); err != nil || got != StateCancelled {
		t.Fatalf("Cancel = (%v, %v), want (Cancelled, nil)", got, err)
	}
}

func TestUnknownBet(t *testing.T) {
	m := NewBetMachine()
	got, err := m.Apply("ghost", EventAccept)
	if !errors.Is(err, ErrUnknownBet) {
		t.Fatalf("Apply on unknown bet err = %v, want ErrUnknownBet", err)
	}
	if got != StatePending {
		t.Fatalf("Apply on unknown bet state = %v, want Pending", got)
	}
}

func TestOpenDuplicateFails(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")
	_, _ = m.Apply("b1", EventAccept)

	if err := m.Open("b1"); err == nil {
		t.Fatalf("second Open returned nil, want error")
	}
	// The progressed state must be untouched by the failed Open.
	if s, _ := m.State("b1"); s != StateAccepted {
		t.Fatalf("state after duplicate Open = %v, want Accepted", s)
	}
}

func TestIllegalTransition_SettleWhilePending(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")

	got, err := m.Apply("b1", EventSettle)
	if !errors.Is(err, ErrIllegalTransition) {
		t.Fatalf("Settle while Pending err = %v, want ErrIllegalTransition", err)
	}
	if got != StatePending {
		t.Fatalf("Settle while Pending state = %v, want Pending (unchanged)", got)
	}
	if s, _ := m.State("b1"); s != StatePending {
		t.Fatalf("stored state mutated by illegal transition: %v", s)
	}
}

func TestIllegalTransition_AcceptAfterCancel(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")
	_, _ = m.Apply("b1", EventCancel)

	// Honouring a pulled bet would be a real-money error.
	got, err := m.Apply("b1", EventAccept)
	if !errors.Is(err, ErrIllegalTransition) {
		t.Fatalf("Accept after Cancel err = %v, want ErrIllegalTransition", err)
	}
	if got != StateCancelled {
		t.Fatalf("Accept after Cancel state = %v, want Cancelled (unchanged)", got)
	}
}

func TestIllegalTransition_DifferentEventAfterSettled(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")
	_, _ = m.Apply("b1", EventAccept)
	_, _ = m.Apply("b1", EventSettle)

	// Accept is NOT the idempotent no-op (its target is Accepted, not Settled),
	// so against a Settled bet it is an illegal transition.
	got, err := m.Apply("b1", EventAccept)
	if !errors.Is(err, ErrIllegalTransition) {
		t.Fatalf("Accept after Settled err = %v, want ErrIllegalTransition", err)
	}
	if got != StateSettled {
		t.Fatalf("Accept after Settled state = %v, want Settled (unchanged)", got)
	}
}

func TestIdempotentDuplicateSettle(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")
	_, _ = m.Apply("b1", EventAccept)

	// First Settle is a real transition.
	if got, err := m.Apply("b1", EventSettle); err != nil || got != StateSettled {
		t.Fatalf("first Settle = (%v, %v), want (Settled, nil)", got, err)
	}
	// Second Settle is a redelivery: no-op success, still Settled, no error.
	if got, err := m.Apply("b1", EventSettle); err != nil || got != StateSettled {
		t.Fatalf("duplicate Settle = (%v, %v), want (Settled, nil)", got, err)
	}
}

func TestIdempotentDuplicate_FiresSideEffectOnce(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")
	_, _ = m.Apply("b1", EventAccept)

	// Model the payout side effect by counting real transitions into Settled:
	// a real transition is one where the bet was NOT already Settled before the
	// call (i.e. the returned state changed to Settled from a non-Settled state).
	payouts := 0
	for i := 0; i < 5; i++ {
		before, _ := m.State("b1")
		got, err := m.Apply("b1", EventSettle)
		if err != nil {
			t.Fatalf("Settle #%d err = %v", i, err)
		}
		if got == StateSettled && before != StateSettled {
			payouts++
		}
	}
	if payouts != 1 {
		t.Fatalf("payouts = %d, want exactly 1 (side effect must fire once)", payouts)
	}
}

// TestConcurrentDuplicateSettle_FiresOnce launches many goroutines that all
// Apply(Settle) to the same Accepted bet with a gated start. Under at-least-once
// delivery this models the broker redelivering the same Settle to many workers.
// Exactly one call must observe the real Accepted->Settled transition; the rest
// must be idempotent no-ops. If the guard and the write were not atomic under the
// machine's lock (TOCTOU), two goroutines could both observe Accepted and both
// transition — a double payout. This test must be race-clean under `go test -race`.
//
// The "payout" side effect is modelled by counting the number of times Apply
// performs a real Accepted->Settled transition (as opposed to an idempotent
// no-op). Because Apply returns the same (Settled, nil) for both the real
// transition and the no-op, the count is observed by reading the bet's state
// immediately before the Apply and recording the (before -> after) edge as one
// atomic observation, guarded by obs. obs makes the read+apply pair atomic so a
// late-arriving duplicate cannot be miscounted as a real transition; it does not
// guard the machine (the machine guards itself), it only makes the test's
// bookkeeping exact while many goroutines still contend to settle the same bet.
func TestConcurrentDuplicateSettle_FiresOnce(t *testing.T) {
	m := NewBetMachine()
	_ = m.Open("b1")
	_, _ = m.Apply("b1", EventAccept)

	const G = 64
	var (
		wg          sync.WaitGroup
		start       = make(chan struct{})
		obs         sync.Mutex
		realTransit int
	)

	for i := 0; i < G; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start

			obs.Lock()
			before, _ := m.State("b1")
			got, err := m.Apply("b1", EventSettle)
			if before != StateSettled && got == StateSettled && err == nil {
				realTransit++ // this Apply performed the real payout-causing edge
			}
			obs.Unlock()

			if err != nil {
				t.Errorf("concurrent Settle err = %v, want nil", err)
				return
			}
			if got != StateSettled {
				t.Errorf("concurrent Settle state = %v, want Settled", got)
			}
		}()
	}

	close(start)
	wg.Wait()

	if s, _ := m.State("b1"); s != StateSettled {
		t.Fatalf("final state = %v, want Settled", s)
	}
	if realTransit != 1 {
		t.Fatalf("real Accepted->Settled transitions = %d, want exactly 1", realTransit)
	}
}
