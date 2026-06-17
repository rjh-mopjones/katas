package ledger

import (
	"errors"
	"fmt"
	"runtime"
	"sync"
	"testing"
	"time"
)

// TestLedger_RaceStress hammers a pool of accounts with concurrent deposits,
// withdrawals, and transfers — including opposing A→B and B→A transfers that would
// deadlock a naive lock-from-then-lock-to implementation — plus duplicate
// idempotency-key replays. Run it with `go test -race -count=N`.
//
// Invariants asserted after the storm:
//
//   - Conservation: transfers only MOVE money, so the total balance across all
//     accounts changes by exactly (sum of deposits) - (sum of successful
//     withdrawals), and the double-entry CheckInvariant holds (transfer postings
//     net to zero).
//   - Idempotency: replaying a key has no extra effect — we replay each operation's
//     key a second time and require the totals to be unchanged by the replay.
//   - No deadlock: a watchdog (`done` channel + select on time.After) fails the test
//     if the goroutines do not all finish, rather than hanging CI. The time.After is
//     ONLY a deadlock watchdog, never used to synchronise the logic under test.
func TestLedger_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	const (
		nAccounts = 8
		initial   = 1_000_000 // large enough that withdraws/transfers rarely overdraw
	)
	workers := 16 * runtime.GOMAXPROCS(0)
	if workers < 32 {
		workers = 32
	}
	const iters = 300

	l := New()
	acct := func(i int) string { return fmt.Sprintf("acc-%02d", i) }
	for i := 0; i < nAccounts; i++ {
		if err := l.Open(acct(i), initial); err != nil {
			t.Fatalf("Open: %v", err)
		}
	}
	startTotal := int64(nAccounts) * initial

	// Per-goroutine sums of the net external flow (deposits minus successful
	// withdrawals) this goroutine caused. Transfers do not change the global total,
	// so they are excluded. Merged after the run; no shared mutable counters.
	netExternal := make([]int64, workers)

	start := make(chan struct{})
	var wg sync.WaitGroup
	for w := 0; w < workers; w++ {
		w := w
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			var net int64
			for i := 0; i < iters; i++ {
				a := (w + i) % nAccounts
				b := (w + i + 1 + i%(nAccounts-1)) % nAccounts
				key := fmt.Sprintf("k-%d-%d", w, i)
				amt := int64(1 + (w*7+i)%50)

				switch (w + i) % 4 {
				case 0:
					if err := l.Deposit(key, acct(a), amt); err != nil {
						t.Errorf("Deposit: %v", err)
						return
					}
					// Duplicate replay: must be a no-op.
					if err := l.Deposit(key, acct(a), amt); err != nil {
						t.Errorf("Deposit replay: %v", err)
						return
					}
					net += amt
				case 1:
					err := l.Withdraw(key, acct(a), amt)
					replay := l.Withdraw(key, acct(a), amt)
					if !errors.Is(err, replay) && err != replay {
						t.Errorf("Withdraw replay outcome differs: %v vs %v", err, replay)
						return
					}
					if err == nil {
						net -= amt
					} else if !errors.Is(err, ErrInsufficientFunds) {
						t.Errorf("Withdraw: unexpected %v", err)
						return
					}
				default:
					// Transfers (incl. opposing directions across the same pair on
					// different goroutines) — global total unchanged either way.
					err := l.Transfer(key, acct(a), acct(b), amt)
					replay := l.Transfer(key, acct(a), acct(b), amt)
					if !errors.Is(err, replay) && err != replay {
						t.Errorf("Transfer replay outcome differs: %v vs %v", err, replay)
						return
					}
					if err != nil && !errors.Is(err, ErrInsufficientFunds) && !errors.Is(err, ErrSameAccount) {
						t.Errorf("Transfer: unexpected %v", err)
						return
					}
				}
			}
			netExternal[w] = net
		}()
	}

	// Deadlock watchdog: all goroutines must finish well within the budget.
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()
	close(start)
	select {
	case <-done:
	case <-time.After(30 * time.Second):
		t.Fatal("deadlock: workers did not finish within 30s")
	}

	var wantDelta int64
	for _, n := range netExternal {
		wantDelta += n
	}

	// Sum the live balances and compare to the expected total.
	var total int64
	for i := 0; i < nAccounts; i++ {
		bal, err := l.Balance(acct(i))
		if err != nil {
			t.Fatalf("Balance: %v", err)
		}
		total += bal
	}
	if want := startTotal + wantDelta; total != want {
		t.Fatalf("conservation violated: total=%d want=%d (start=%d netExternal=%d)",
			total, want, startTotal, wantDelta)
	}

	if err := l.CheckInvariant(); err != nil {
		t.Fatalf("CheckInvariant: %v", err)
	}
}
