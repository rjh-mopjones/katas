package ledger

import (
	"errors"
	"fmt"
	"math/rand"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestOpenAndBalance(t *testing.T) {
	l := New()
	if err := l.Open("alice", 1000); err != nil {
		t.Fatalf("Open: %v", err)
	}
	bal, err := l.Balance("alice")
	if err != nil {
		t.Fatalf("Balance: %v", err)
	}
	if bal != 1000 {
		t.Fatalf("Balance = %d, want 1000", bal)
	}
}

func TestOpenDuplicate(t *testing.T) {
	l := New()
	if err := l.Open("alice", 0); err != nil {
		t.Fatalf("Open: %v", err)
	}
	err := l.Open("alice", 500)
	if !errors.Is(err, ErrAccountExists) {
		t.Fatalf("Open duplicate err = %v, want ErrAccountExists", err)
	}
}

func TestBalanceUnknownAccount(t *testing.T) {
	l := New()
	_, err := l.Balance("ghost")
	if !errors.Is(err, ErrUnknownAccount) {
		t.Fatalf("Balance unknown err = %v, want ErrUnknownAccount", err)
	}
}

func TestDepositAndWithdraw(t *testing.T) {
	l := New()
	l.Open("alice", 100)

	if err := l.Deposit("d1", "alice", 50); err != nil {
		t.Fatalf("Deposit: %v", err)
	}
	if bal, _ := l.Balance("alice"); bal != 150 {
		t.Fatalf("after deposit balance = %d, want 150", bal)
	}

	if err := l.Withdraw("w1", "alice", 30); err != nil {
		t.Fatalf("Withdraw: %v", err)
	}
	if bal, _ := l.Balance("alice"); bal != 120 {
		t.Fatalf("after withdraw balance = %d, want 120", bal)
	}
}

func TestWithdrawInsufficientFunds(t *testing.T) {
	l := New()
	l.Open("alice", 100)

	err := l.Withdraw("w1", "alice", 101)
	if !errors.Is(err, ErrInsufficientFunds) {
		t.Fatalf("Withdraw overdraw err = %v, want ErrInsufficientFunds", err)
	}
	if bal, _ := l.Balance("alice"); bal != 100 {
		t.Fatalf("balance changed on rejected withdraw: %d, want 100", bal)
	}
}

func TestDepositUnknownAccount(t *testing.T) {
	l := New()
	err := l.Deposit("d1", "ghost", 10)
	if !errors.Is(err, ErrUnknownAccount) {
		t.Fatalf("Deposit unknown err = %v, want ErrUnknownAccount", err)
	}
}

func TestTransferHappyPath(t *testing.T) {
	l := New()
	l.Open("alice", 100)
	l.Open("bob", 0)

	if err := l.Transfer("t1", "alice", "bob", 40); err != nil {
		t.Fatalf("Transfer: %v", err)
	}
	if bal, _ := l.Balance("alice"); bal != 60 {
		t.Fatalf("alice balance = %d, want 60", bal)
	}
	if bal, _ := l.Balance("bob"); bal != 40 {
		t.Fatalf("bob balance = %d, want 40", bal)
	}
	if err := l.CheckInvariant(); err != nil {
		t.Fatalf("invariant broken: %v", err)
	}
}

func TestTransferSameAccountRejected(t *testing.T) {
	l := New()
	l.Open("alice", 100)
	err := l.Transfer("t1", "alice", "alice", 10)
	if !errors.Is(err, ErrSameAccount) {
		t.Fatalf("same-account transfer err = %v, want ErrSameAccount", err)
	}
}

func TestTransferInsufficientFunds(t *testing.T) {
	l := New()
	l.Open("alice", 30)
	l.Open("bob", 0)

	err := l.Transfer("t1", "alice", "bob", 31)
	if !errors.Is(err, ErrInsufficientFunds) {
		t.Fatalf("Transfer overdraw err = %v, want ErrInsufficientFunds", err)
	}
	if bal, _ := l.Balance("alice"); bal != 30 {
		t.Fatalf("alice changed on rejected transfer: %d", bal)
	}
	if bal, _ := l.Balance("bob"); bal != 0 {
		t.Fatalf("bob changed on rejected transfer: %d", bal)
	}
}

// --- Idempotency ---

func TestDepositIdempotent(t *testing.T) {
	l := New()
	l.Open("alice", 0)

	for i := 0; i < 5; i++ {
		if err := l.Deposit("same-key", "alice", 100); err != nil {
			t.Fatalf("Deposit #%d: %v", i, err)
		}
	}
	if bal, _ := l.Balance("alice"); bal != 100 {
		t.Fatalf("duplicate deposits applied %d, want 100 (one effect)", bal)
	}
}

func TestWithdrawIdempotent(t *testing.T) {
	l := New()
	l.Open("alice", 500)

	for i := 0; i < 5; i++ {
		if err := l.Withdraw("same-key", "alice", 100); err != nil {
			t.Fatalf("Withdraw #%d: %v", i, err)
		}
	}
	if bal, _ := l.Balance("alice"); bal != 400 {
		t.Fatalf("duplicate withdraws applied %d, want 400 (one effect)", bal)
	}
}

func TestWithdrawIdempotentReplaysRejection(t *testing.T) {
	l := New()
	l.Open("alice", 50)

	first := l.Withdraw("k", "alice", 100)
	if !errors.Is(first, ErrInsufficientFunds) {
		t.Fatalf("first withdraw err = %v, want ErrInsufficientFunds", first)
	}
	// Even after funding the account, the same key must replay the original
	// rejection — it is the same logical command, already answered.
	l.Deposit("fund", "alice", 1000)
	second := l.Withdraw("k", "alice", 100)
	if !errors.Is(second, ErrInsufficientFunds) {
		t.Fatalf("replayed withdraw err = %v, want ErrInsufficientFunds", second)
	}
	if bal, _ := l.Balance("alice"); bal != 1050 {
		t.Fatalf("balance = %d, want 1050 (only the deposit took effect)", bal)
	}
}

func TestTransferIdempotent(t *testing.T) {
	l := New()
	l.Open("alice", 100)
	l.Open("bob", 0)

	for i := 0; i < 5; i++ {
		if err := l.Transfer("same-key", "alice", "bob", 40); err != nil {
			t.Fatalf("Transfer #%d: %v", i, err)
		}
	}
	if bal, _ := l.Balance("alice"); bal != 60 {
		t.Fatalf("alice = %d, want 60 (one effect)", bal)
	}
	if bal, _ := l.Balance("bob"); bal != 40 {
		t.Fatalf("bob = %d, want 40 (one effect)", bal)
	}
	if err := l.CheckInvariant(); err != nil {
		t.Fatalf("invariant broken: %v", err)
	}
}

// --- Concurrency: no lost updates ---

func TestNoLostUpdates(t *testing.T) {
	l := New()
	l.Open("alice", 0)

	const (
		goroutines = 16
		perGoro    = 1000
	)

	var wg sync.WaitGroup
	start := make(chan struct{})
	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func(g int) {
			defer wg.Done()
			<-start
			for i := 0; i < perGoro; i++ {
				key := fmt.Sprintf("g%d-i%d", g, i) // distinct keys: every deposit counts
				if err := l.Deposit(key, "alice", 1); err != nil {
					t.Errorf("Deposit: %v", err)
					return
				}
			}
		}(g)
	}
	close(start)
	wg.Wait()

	want := int64(goroutines * perGoro)
	if bal, _ := l.Balance("alice"); bal != want {
		t.Fatalf("lost updates: balance = %d, want %d", bal, want)
	}
}

func TestConcurrentDuplicateDeposits_OneEffect(t *testing.T) {
	// Many goroutines deliver the SAME idemKey concurrently: at-least-once
	// redelivery. Exactly one must take effect (TOCTOU on the seen-set).
	l := New()
	l.Open("alice", 0)

	const goroutines = 32
	var wg sync.WaitGroup
	start := make(chan struct{})
	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			_ = l.Deposit("dup-key", "alice", 100)
		}()
	}
	close(start)
	wg.Wait()

	if bal, _ := l.Balance("alice"); bal != 100 {
		t.Fatalf("duplicate concurrent deposits = %d, want 100 (exactly one effect)", bal)
	}
}

// --- Concurrency: deadlock-free opposing transfers ---

func TestDeadlockFreeTransfers(t *testing.T) {
	l := New()
	l.Open("A", 1_000_000)
	l.Open("B", 1_000_000)

	const (
		goroutines = 16
		perGoro    = 500
	)

	var wg sync.WaitGroup
	start := make(chan struct{})

	// Half the goroutines push A->B, half push B->A, simultaneously. A naive
	// "lock from then lock to" would deadlock here; sorted acquisition does not.
	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		dir := g % 2
		go func(g, dir int) {
			defer wg.Done()
			<-start
			for i := 0; i < perGoro; i++ {
				key := fmt.Sprintf("g%d-i%d", g, i)
				if dir == 0 {
					_ = l.Transfer(key, "A", "B", 1)
				} else {
					_ = l.Transfer(key, "B", "A", 1)
				}
			}
		}(g, dir)
	}

	close(start)

	// Guard against a hang WITHOUT sleeping: wait on a done channel, fail if the
	// work has not completed within a generous bound.
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(30 * time.Second):
		t.Fatal("transfers did not complete: likely deadlock")
	}

	// Conservation: A+B total is unchanged regardless of how the moves interleaved.
	balA, _ := l.Balance("A")
	balB, _ := l.Balance("B")
	if balA+balB != 2_000_000 {
		t.Fatalf("money not conserved: A=%d B=%d total=%d, want 2000000", balA, balB, balA+balB)
	}
	if err := l.CheckInvariant(); err != nil {
		t.Fatalf("invariant broken: %v", err)
	}
}

// --- Double-entry conservation under a randomized workload ---

func TestRandomizedWorkloadConserves(t *testing.T) {
	l := New()
	const accounts = 8
	ids := make([]string, accounts)
	var seeded int64
	for i := 0; i < accounts; i++ {
		id := fmt.Sprintf("acct-%d", i)
		ids[i] = id
		l.Open(id, 100_000)
		seeded += 100_000
	}

	const goroutines = 12
	var wg sync.WaitGroup
	start := make(chan struct{})
	var keyCounter atomic.Uint64

	// External (boundary) movements are tracked so we can reconcile total balance.
	var externalDelta atomic.Int64

	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func(g int) {
			defer wg.Done()
			rng := rand.New(rand.NewSource(int64(g) + 1))
			<-start
			for i := 0; i < 2000; i++ {
				key := fmt.Sprintf("k-%d", keyCounter.Add(1))
				switch rng.Intn(3) {
				case 0:
					amt := int64(rng.Intn(100))
					if l.Deposit(key, ids[rng.Intn(accounts)], amt) == nil {
						externalDelta.Add(amt)
					}
				case 1:
					amt := int64(rng.Intn(100))
					if l.Withdraw(key, ids[rng.Intn(accounts)], amt) == nil {
						externalDelta.Add(-amt)
					}
				default:
					from := ids[rng.Intn(accounts)]
					to := ids[rng.Intn(accounts)]
					_ = l.Transfer(key, from, to, int64(rng.Intn(100)))
				}
			}
		}(g)
	}
	close(start)
	wg.Wait()

	// Per-account entries reconcile to balances, and transfers net to zero.
	if err := l.CheckInvariant(); err != nil {
		t.Fatalf("invariant broken: %v", err)
	}

	// Total balance == seeded + net external movements (transfers cancel out).
	var total int64
	for _, id := range ids {
		bal, _ := l.Balance(id)
		total += bal
	}
	want := seeded + externalDelta.Load()
	if total != want {
		t.Fatalf("total balance = %d, want %d (seed %d + external %d)", total, want, seeded, externalDelta.Load())
	}
}
