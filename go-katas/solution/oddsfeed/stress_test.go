package oddsfeed

import (
	"context"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
)

// TestConsumer_RaceStress runs the worker pool under high contention: a buffered
// input channel fed by many producer goroutines, drained by many workers, each
// invoking the handler. It exercises BOTH shutdown paths in separate sub-runs —
// clean drain (close input) and cancellation — and after each run polls
// runtime.NumGoroutine back to baseline to prove no worker leaked. On the drain
// path it also asserts the handler ran exactly once per message. Run under
// `go test -race -count=N`.
func TestConsumer_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	baseline := waitForGoroutines(runtime.NumGoroutine())

	procs := runtime.GOMAXPROCS(0)
	workers := 4 * procs
	if workers < 16 {
		workers = 16
	}
	producers := 4 * procs
	if producers < 8 {
		producers = 8
	}
	const perProducer = 5000

	// Drain path: many producers fill a buffered channel and close it; every
	// message must be handled exactly once across all runs.
	runDrain := func() {
		var processed atomic.Int64
		c := NewConsumer(workers, func(_ context.Context, _ Message) {
			processed.Add(1)
		})

		in := make(chan Message, 1024)
		var pwg sync.WaitGroup
		for p := 0; p < producers; p++ {
			pwg.Add(1)
			go func() {
				defer pwg.Done()
				for i := 0; i < perProducer; i++ {
					in <- Message{Market: "m", Payload: "p"}
				}
			}()
		}
		go func() {
			pwg.Wait()
			close(in)
		}()

		if err := c.Run(context.Background(), in); err != nil {
			t.Fatalf("Run (drain) returned %v, want nil", err)
		}
		if want := int64(producers * perProducer); processed.Load() != want {
			t.Fatalf("handler invoked %d times, want %d", processed.Load(), want)
		}
		if got := waitForGoroutines(baseline); got > baseline {
			t.Fatalf("drain path leaked goroutines: got %d, want <= %d", got, baseline)
		}
	}

	// Cancel path: producers keep feeding a channel that is never closed; the
	// context is cancelled to stop the pool. Run must return context.Canceled
	// and every worker (and producer) must unwind without leaking.
	runCancel := func() {
		c := NewConsumer(workers, func(_ context.Context, _ Message) {})

		in := make(chan Message, 1024)
		ctx, cancel := context.WithCancel(context.Background())

		var pwg sync.WaitGroup
		for p := 0; p < producers; p++ {
			pwg.Add(1)
			go func() {
				defer pwg.Done()
				for {
					select {
					case <-ctx.Done():
						return
					case in <- Message{Market: "m", Payload: "p"}:
					}
				}
			}()
		}

		errCh := make(chan error, 1)
		go func() { errCh <- c.Run(ctx, in) }()
		cancel()
		if err := <-errCh; err != context.Canceled {
			t.Fatalf("Run (cancel) returned %v, want context.Canceled", err)
		}
		pwg.Wait()
		if got := waitForGoroutines(baseline); got > baseline {
			t.Fatalf("cancel path leaked goroutines: got %d, want <= %d", got, baseline)
		}
	}

	for i := 0; i < 3; i++ {
		runDrain()
		runCancel()
	}
}
