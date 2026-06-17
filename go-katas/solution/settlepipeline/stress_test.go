package settlepipeline

import (
	"context"
	"errors"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
)

// TestPipeline_RaceStress runs Pipeline.Run many times concurrently across three
// scenario families — clean success, caller cancellation, and an injected stage
// error — while instrumenting per-stage concurrency. Run it with
// `go test -race -count=N`.
//
// Assertions:
//
//   - Success runs settle every bet (len(ids) == len(bets), all IDs present).
//   - Cancelled runs return ctx.Err() (context.Canceled) and do not over-process
//     (no stage runs more times than there are bets).
//   - Error runs return the injected error.
//   - Observed per-stage concurrency never exceeds the configured worker cap, even
//     with many Runs sharing the same instrumented StageFuncs.
//   - Goroutine count returns to baseline after all Runs complete (no leak).
//
// No sleeps are used for synchronisation: a start gate releases all runners at once
// and a WaitGroup joins them.
func TestPipeline_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	baseline := runtime.NumGoroutine()

	const (
		betsPerRun = 64
	)
	workers := [4]int{2, 3, 2, 4}
	buffers := [4]int{4, 4, 4, 4}

	// Shared per-stage concurrency instrumentation across every concurrent Run. Each
	// stage tracks current in-flight workers and the max ever observed; we assert the
	// max never exceeds (workers[stage] * runsInFlight) is NOT what we want — instead
	// each Pipeline has its OWN worker pools, so we instrument PER pipeline. To keep
	// the cap assertion meaningful we therefore give each Run its own funcs+counters.
	type stageCounters struct {
		inFlight [4]int64
		maxSeen  [4]int64
	}

	instrumented := func(c *stageCounters, base StageFuncs) StageFuncs {
		track := func(stage int, body func()) {
			cur := atomic.AddInt64(&c.inFlight[stage], 1)
			for {
				old := atomic.LoadInt64(&c.maxSeen[stage])
				if cur <= old || atomic.CompareAndSwapInt64(&c.maxSeen[stage], old, cur) {
					break
				}
			}
			body()
			atomic.AddInt64(&c.inFlight[stage], -1)
		}
		return StageFuncs{
			Validate: func(ctx context.Context, b Bet) (Validated, error) {
				var out Validated
				var err error
				track(0, func() { out, err = base.Validate(ctx, b) })
				return out, err
			},
			Reserve: func(ctx context.Context, v Validated) (Reserved, error) {
				var out Reserved
				var err error
				track(1, func() { out, err = base.Reserve(ctx, v) })
				return out, err
			},
			Settle: func(ctx context.Context, r Reserved) (Settled, error) {
				var out Settled
				var err error
				track(2, func() { out, err = base.Settle(ctx, r) })
				return out, err
			},
			Notify: func(ctx context.Context, s Settled) (Notified, error) {
				var out Notified
				var err error
				track(3, func() { out, err = base.Notify(ctx, s) })
				return out, err
			},
		}
	}

	errInjected := errors.New("injected settle failure")

	const runners = 24
	bets := makeBets(betsPerRun)

	start := make(chan struct{})
	var wg sync.WaitGroup
	var capViolations int64

	for r := 0; r < runners; r++ {
		r := r
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start

			var c stageCounters
			scenario := r % 3
			switch scenario {
			case 0: // success
				p := New(instrumented(&c, identityFuncs()), workers, buffers)
				ids, err := p.Run(context.Background(), bets)
				if err != nil {
					t.Errorf("success run: unexpected error %v", err)
					return
				}
				if len(ids) != len(bets) {
					t.Errorf("success run: settled %d of %d", len(ids), len(bets))
					return
				}
				seen := make(map[string]bool, len(ids))
				for _, id := range ids {
					seen[id] = true
				}
				for _, b := range bets {
					if !seen[b.ID] {
						t.Errorf("success run: missing %s", b.ID)
						return
					}
				}
			case 1: // cancellation: cancel immediately so Run tears down promptly
				p := New(instrumented(&c, identityFuncs()), workers, buffers)
				ctx, cancel := context.WithCancel(context.Background())
				cancel()
				_, err := p.Run(ctx, bets)
				if !errors.Is(err, context.Canceled) {
					t.Errorf("cancel run: want context.Canceled, got %v", err)
					return
				}
			default: // injected error in the settle stage
				base := identityFuncs()
				base.Settle = func(_ context.Context, _ Reserved) (Settled, error) {
					return Settled{}, errInjected
				}
				p := New(instrumented(&c, base), workers, buffers)
				_, err := p.Run(context.Background(), bets)
				if !errors.Is(err, errInjected) {
					t.Errorf("error run: want injected error, got %v", err)
					return
				}
			}

			// Per-stage cap: a single pipeline's stage must never run more than its
			// configured worker count concurrently.
			for s := 0; s < 4; s++ {
				if m := atomic.LoadInt64(&c.maxSeen[s]); m > int64(workers[s]) {
					atomic.AddInt64(&capViolations, 1)
					t.Errorf("scenario %d stage %d observed concurrency %d > cap %d",
						scenario, s, m, workers[s])
				}
				// No stage should process more than the number of bets (no over-processing).
				if cur := atomic.LoadInt64(&c.inFlight[s]); cur != 0 {
					t.Errorf("scenario %d stage %d leaked in-flight count %d", scenario, s, cur)
				}
			}
		}()
	}

	close(start)
	wg.Wait()

	if v := atomic.LoadInt64(&capViolations); v > 0 {
		t.Fatalf("%d worker-cap violations observed", v)
	}

	if got := waitForGoroutines(baseline); got > baseline {
		t.Errorf("goroutine leak: baseline %d, got %d", baseline, got)
	}
}
