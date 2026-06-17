package betmachine

import (
	"fmt"
	"runtime"
	"sync"
	"testing"
)

// legalState reports whether s is one of the machine's defined states.
func legalState(s State) bool {
	switch s {
	case StatePending, StateAccepted, StateSettled, StateRejected, StateCancelled:
		return true
	default:
		return false
	}
}

// TestBetMachine_RaceStress drives high contention across many bets with a mix
// of all public operations, gated to start together.
//
// Two workloads run concurrently:
//
//   - "Settle race" bets: each is Opened then Accepted, then G goroutines all
//     Apply(Settle) to it (modelling at-least-once redelivery). Exactly one
//     Apply may observe the real Accepted->Settled edge; the rest must be
//     idempotent no-ops. We count the real edge using the existing test's
//     technique — a per-bet observation mutex makes the (read-before, Apply)
//     pair atomic so a late duplicate cannot be miscounted — and assert each bet
//     ends Settled with exactly one real transition.
//   - "Mixed" bets: many goroutines Open distinct bets and Apply a varied stream
//     of Accept/Settle/Reject/Cancel plus State reads. Apply must never panic
//     and every bet must end in a legal state regardless of interleaving.
//
// Run under `go test -race -count=N` for the TOCTOU/data-race signal.
func TestBetMachine_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	procs := runtime.GOMAXPROCS(0)
	settlersPerBet := 4 * procs
	if settlersPerBet < 16 {
		settlersPerBet = 16
	}

	const settleBets = 32

	m := NewBetMachine()

	// Pre-open and accept the settle-race bets.
	betID := func(i int) string { return fmt.Sprintf("settle-%d", i) }
	for i := 0; i < settleBets; i++ {
		if err := m.Open(betID(i)); err != nil {
			t.Fatalf("Open(%s): %v", betID(i), err)
		}
		if _, err := m.Apply(betID(i), EventAccept); err != nil {
			t.Fatalf("Accept(%s): %v", betID(i), err)
		}
	}

	// Per-bet observation mutexes + real-transition counters.
	obs := make([]sync.Mutex, settleBets)
	realTransit := make([]int, settleBets)

	var wg sync.WaitGroup
	start := make(chan struct{})

	for i := 0; i < settleBets; i++ {
		for s := 0; s < settlersPerBet; s++ {
			wg.Add(1)
			go func(bet int) {
				defer wg.Done()
				<-start
				obs[bet].Lock()
				before, _ := m.State(betID(bet))
				got, err := m.Apply(betID(bet), EventSettle)
				if before != StateSettled && got == StateSettled && err == nil {
					realTransit[bet]++
				}
				obs[bet].Unlock()
				if err != nil {
					t.Errorf("Settle(%s) err = %v, want nil", betID(bet), err)
					return
				}
				if got != StateSettled {
					t.Errorf("Settle(%s) state = %v, want Settled", betID(bet), got)
				}
			}(i)
		}
	}

	// Mixed workload: each goroutine owns a disjoint id space, so Open never
	// collides, and applies a varied event stream across its bets.
	events := []Event{EventAccept, EventSettle, EventReject, EventCancel}
	mixers := 4 * procs
	if mixers < 16 {
		mixers = 16
	}
	const mixedBetsPerGoro = 500

	for x := 0; x < mixers; x++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			<-start
			for i := 0; i < mixedBetsPerGoro; i++ {
				bet := fmt.Sprintf("mix-%d-%d", id, i)
				if err := m.Open(bet); err != nil {
					t.Errorf("Open(%s) err = %v, want nil", bet, err)
					return
				}
				// Apply a few events (some illegal/duplicate by design) and read
				// state; none of this may panic and the bet must stay legal.
				for j := 0; j < 4; j++ {
					_, _ = m.Apply(bet, events[(i+j)%len(events)])
				}
				if s, ok := m.State(bet); !ok || !legalState(s) {
					t.Errorf("State(%s) = (%v, %v), want a legal known state", bet, s, ok)
					return
				}
			}
		}(x)
	}

	close(start)
	wg.Wait()

	for i := 0; i < settleBets; i++ {
		if s, _ := m.State(betID(i)); s != StateSettled {
			t.Fatalf("%s final state = %v, want Settled", betID(i), s)
		}
		if realTransit[i] != 1 {
			t.Fatalf("%s real Accepted->Settled transitions = %d, want exactly 1", betID(i), realTransit[i])
		}
	}
}
