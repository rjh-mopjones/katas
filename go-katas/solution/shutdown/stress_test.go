package shutdown

import (
	"context"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// waitForGoroutines polls runtime.NumGoroutine until it drops to at most want,
// returning the final observed count. It is a bounded retry that only gives the
// scheduler time to reap goroutines already returning; it does not synchronise
// the logic under test.
func waitForGoroutines(want int) int {
	got := runtime.NumGoroutine()
	for i := 0; i < 200 && got > want; i++ {
		runtime.Gosched()
		time.Sleep(time.Millisecond)
		got = runtime.NumGoroutine()
	}
	return got
}

// TestServer_RaceStress drives many concurrent Submitters against a worker pool
// while another goroutine calls Shutdown (twice, for idempotency). Under -race it
// must show no send-on-closed panic; Submit after shutdown must return
// ErrShuttingDown; every job that Submit accepted (returned nil) must be processed
// (the no-loss invariant on the clean path); and no goroutine may leak.
func TestServer_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	baseline := runtime.NumGoroutine()

	const (
		workers     = 6
		queueSize   = 32
		submitters  = 48
		perSubmit   = 200
		goschedSpin = 80
	)

	var processed atomic.Int64
	s := NewServer(workers, queueSize, func(Job) {
		processed.Add(1)
	})
	s.Start()

	start := make(chan struct{})
	var wg sync.WaitGroup
	wg.Add(submitters)

	var accepted atomic.Int64 // Submits that returned nil (job is now owned by the server)
	var badErr atomic.Int64   // errors other than nil / ErrShuttingDown

	for sub := 0; sub < submitters; sub++ {
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < perSubmit; i++ {
				switch err := s.Submit(Job{ID: "x"}); err {
				case nil:
					accepted.Add(1)
				case ErrShuttingDown:
				default:
					badErr.Add(1)
				}
			}
		}()
	}

	// Shutdown races the live submitters. We give it a generous context so the
	// clean drain completes (so the no-loss invariant is the one under test).
	shutdownDone := make(chan error, 1)
	go func() {
		<-start
		for i := 0; i < goschedSpin; i++ {
			runtime.Gosched()
		}
		err1 := s.Shutdown(context.Background())
		err2 := s.Shutdown(context.Background()) // idempotent under concurrency.
		if err1 != nil {
			shutdownDone <- err1
			return
		}
		shutdownDone <- err2
	}()

	close(start)
	wg.Wait()

	if err := <-shutdownDone; err != nil {
		t.Fatalf("Shutdown returned error on the clean path: %v", err)
	}

	if n := badErr.Load(); n != 0 {
		t.Fatalf("Submit returned %d unexpected errors (want only nil/ErrShuttingDown)", n)
	}

	// No-loss invariant: every accepted job was processed before Shutdown returned.
	if got, want := processed.Load(), accepted.Load(); got != want {
		t.Fatalf("processed %d jobs but %d were accepted; queued work was lost", got, want)
	}

	// Submit after shutdown must be a clean rejection, never a panic.
	if err := s.Submit(Job{ID: "late"}); err != ErrShuttingDown {
		t.Fatalf("Submit after Shutdown: got %v, want ErrShuttingDown", err)
	}

	if g := waitForGoroutines(baseline); g > baseline {
		t.Fatalf("goroutine leak: started near %d, still %d after stress", baseline, g)
	}
}
