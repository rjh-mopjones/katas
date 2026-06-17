package settlepipeline

import (
	"context"
	"errors"
	"fmt"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// waitForGoroutines polls runtime.NumGoroutine until it drops to at most want,
// returning the final observed count. The polling is a bounded retry that only
// gives the scheduler time to reap goroutines that are already returning; it
// does not synchronise the logic under test.
func waitForGoroutines(want int) int {
	got := runtime.NumGoroutine()
	for i := 0; i < 200 && got > want; i++ {
		runtime.Gosched()
		time.Sleep(time.Millisecond)
		got = runtime.NumGoroutine()
	}
	return got
}

// identityFuncs builds StageFuncs that pass each bet straight through with a
// trivial transform, so a test can override only the stage it cares about.
func identityFuncs() StageFuncs {
	return StageFuncs{
		Validate: func(_ context.Context, b Bet) (Validated, error) { return Validated{Bet: b}, nil },
		Reserve: func(_ context.Context, v Validated) (Reserved, error) {
			return Reserved{Bet: v.Bet, Reserved: v.Bet.Stake}, nil
		},
		Settle: func(_ context.Context, r Reserved) (Settled, error) {
			return Settled{Bet: r.Bet, Payout: r.Reserved * 2}, nil
		},
		Notify: func(_ context.Context, s Settled) (Notified, error) { return Notified{Bet: s.Bet}, nil },
	}
}

func makeBets(n int) []Bet {
	bets := make([]Bet, n)
	for i := range bets {
		bets[i] = Bet{ID: fmt.Sprintf("bet-%d", i), Stake: int64(i + 1)}
	}
	return bets
}

func TestRun_ProcessesAllBets(t *testing.T) {
	baseline := runtime.NumGoroutine()

	const n = 500
	p := New(identityFuncs(), [4]int{4, 4, 4, 4}, [4]int{2, 2, 2, 2})

	got, err := p.Run(context.Background(), makeBets(n))
	if err != nil {
		t.Fatalf("Run returned error: %v", err)
	}
	if len(got) != n {
		t.Fatalf("settled %d bets, want %d", len(got), n)
	}

	// Every bet ID must appear exactly once — no drop, no duplicate.
	seen := make(map[string]int, n)
	for _, id := range got {
		seen[id]++
	}
	for i := 0; i < n; i++ {
		id := fmt.Sprintf("bet-%d", i)
		if c := seen[id]; c != 1 {
			t.Fatalf("bet %s settled %d times, want exactly 1", id, c)
		}
	}

	if g := waitForGoroutines(baseline); g > baseline {
		t.Fatalf("goroutine leak after success: baseline %d, after %d", baseline, g)
	}
}

func TestRun_CancellationPropagatesAndStopsWork(t *testing.T) {
	baseline := runtime.NumGoroutine()

	ctx, cancel := context.WithCancel(context.Background())

	// gate blocks the settle stage so we can cancel mid-flight; settleCalls
	// counts how many bets actually entered settle. Once cancelled, no further
	// settle work must start.
	gate := make(chan struct{})
	var settleCalls int64

	fns := identityFuncs()
	fns.Settle = func(ctx context.Context, r Reserved) (Settled, error) {
		atomic.AddInt64(&settleCalls, 1)
		select {
		case <-gate:
			return Settled{Bet: r.Bet, Payout: r.Reserved}, nil
		case <-ctx.Done():
			return Settled{}, ctx.Err()
		}
	}

	// Single worker on settle so at most one bet can be parked at the gate;
	// everything behind it is held upstream by backpressure.
	p := New(fns, [4]int{2, 2, 1, 2}, [4]int{1, 1, 1, 1})

	const n = 200
	done := make(chan struct{})
	var got []string
	var runErr error
	go func() {
		got, runErr = p.Run(ctx, makeBets(n))
		close(done)
	}()

	// Wait (bounded) until settle has been entered at least once, proving the
	// pipeline is live and parked at the gate, then cancel.
	for i := 0; i < 1000 && atomic.LoadInt64(&settleCalls) == 0; i++ {
		runtime.Gosched()
		time.Sleep(time.Millisecond)
	}
	cancel()

	<-done

	if !errors.Is(runErr, context.Canceled) {
		t.Fatalf("Run error: got %v, want context.Canceled", runErr)
	}
	if got != nil {
		t.Fatalf("expected nil result on cancel, got %v", got)
	}
	// With a single settle worker parked at the gate, cancellation must stop
	// further settle calls well before all n bets reach it.
	if c := atomic.LoadInt64(&settleCalls); c >= n {
		t.Fatalf("settle kept working after cancel: %d calls (n=%d)", c, n)
	}

	if g := waitForGoroutines(baseline); g > baseline {
		t.Fatalf("goroutine leak after cancel: baseline %d, after %d", baseline, g)
	}
}

func TestRun_FirstErrorShortCircuits(t *testing.T) {
	baseline := runtime.NumGoroutine()

	wantErr := errors.New("reserve declined: insufficient funds")
	var reserveCalls int64

	fns := identityFuncs()
	fns.Reserve = func(_ context.Context, v Validated) (Reserved, error) {
		atomic.AddInt64(&reserveCalls, 1)
		if v.Bet.ID == "bet-3" {
			return Reserved{}, wantErr
		}
		return Reserved{Bet: v.Bet, Reserved: v.Bet.Stake}, nil
	}

	// Single reserve worker so the failing bet short-circuits before all bets
	// have been pulled through reserve.
	p := New(fns, [4]int{1, 1, 1, 1}, [4]int{1, 1, 1, 1})

	const n = 300
	got, err := p.Run(context.Background(), makeBets(n))
	if !errors.Is(err, wantErr) {
		t.Fatalf("Run error: got %v, want %v", err, wantErr)
	}
	if got != nil {
		t.Fatalf("expected nil result on error, got %v", got)
	}
	if c := atomic.LoadInt64(&reserveCalls); c >= n {
		t.Fatalf("pipeline kept feeding after error: %d reserve calls (n=%d)", c, n)
	}

	if g := waitForGoroutines(baseline); g > baseline {
		t.Fatalf("goroutine leak after error: baseline %d, after %d", baseline, g)
	}
}

func TestRun_BoundedConcurrencyPerStage(t *testing.T) {
	const workers = 4

	var inFlight, maxSeen int64
	// release lets all parked workers proceed together once we've observed the
	// peak; closing it broadcasts to every blocked worker.
	release := make(chan struct{})
	var once sync.Once
	var entered int64

	fns := identityFuncs()
	fns.Settle = func(ctx context.Context, r Reserved) (Settled, error) {
		cur := atomic.AddInt64(&inFlight, 1)
		for {
			old := atomic.LoadInt64(&maxSeen)
			if cur <= old || atomic.CompareAndSwapInt64(&maxSeen, old, cur) {
				break
			}
		}
		// Once enough workers are concurrently parked to reveal the cap,
		// release everyone so the test completes without sleeps.
		if atomic.AddInt64(&entered, 1) >= int64(workers) {
			once.Do(func() { close(release) })
		}
		select {
		case <-release:
		case <-ctx.Done():
		}
		atomic.AddInt64(&inFlight, -1)
		return Settled{Bet: r.Bet, Payout: r.Reserved}, nil
	}

	// Feed plenty so more than `workers` bets are available; buffers large
	// enough that the settle stage is the only bottleneck.
	p := New(fns, [4]int{8, 8, workers, 8}, [4]int{16, 16, 16, 16})

	const n = 100
	got, err := p.Run(context.Background(), makeBets(n))
	if err != nil {
		t.Fatalf("Run returned error: %v", err)
	}
	if len(got) != n {
		t.Fatalf("settled %d bets, want %d", len(got), n)
	}
	if m := atomic.LoadInt64(&maxSeen); m > int64(workers) {
		t.Fatalf("observed max concurrency %d exceeds worker cap %d", m, workers)
	}
}
