package shutdown

import (
	"context"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func TestShutdown_DrainsAllJobs(t *testing.T) {
	const n = 200
	var processed atomic.Int64
	s := NewServer(4, 16, func(Job) {
		processed.Add(1)
	})
	s.Start()

	for i := 0; i < n; i++ {
		if err := s.Submit(Job{ID: "j"}); err != nil {
			t.Fatalf("Submit %d returned error: %v", i, err)
		}
	}

	if err := s.Shutdown(context.Background()); err != nil {
		t.Fatalf("Shutdown returned error: %v", err)
	}

	if got := processed.Load(); got != n {
		t.Fatalf("processed %d jobs, want %d (queued work was lost)", got, n)
	}
}

func TestSubmit_AfterShutdown_Rejected(t *testing.T) {
	s := NewServer(2, 4, func(Job) {})
	s.Start()

	if err := s.Shutdown(context.Background()); err != nil {
		t.Fatalf("Shutdown returned error: %v", err)
	}

	if err := s.Submit(Job{ID: "late"}); err != ErrShuttingDown {
		t.Fatalf("Submit after Shutdown: got %v, want ErrShuttingDown", err)
	}
}

func TestShutdown_NoSendOnClosedPanic(t *testing.T) {
	s := NewServer(4, 8, func(Job) {})
	s.Start()

	// Many goroutines hammer Submit while Shutdown closes the jobs channel. A
	// send-on-closed bug would panic and crash the test; a clean design either
	// enqueues or returns ErrShuttingDown.
	var wg sync.WaitGroup
	const submitters = 16
	wg.Add(submitters)
	start := make(chan struct{})
	for i := 0; i < submitters; i++ {
		go func() {
			defer wg.Done()
			<-start
			for j := 0; j < 50; j++ {
				if err := s.Submit(Job{ID: "x"}); err != nil && err != ErrShuttingDown {
					t.Errorf("Submit returned unexpected error: %v", err)
				}
			}
		}()
	}

	close(start)
	if err := s.Shutdown(context.Background()); err != nil {
		t.Fatalf("Shutdown returned error: %v", err)
	}

	wg.Wait() // proves the submitters all terminated, no goroutine wedged.
}

func TestShutdown_RespectsContextDeadline(t *testing.T) {
	// block gates the single worker so the drain cannot complete until the test
	// allows it — making the ctx-deadline path deterministic (no real sleeps for
	// synchronization).
	block := make(chan struct{})
	s := NewServer(1, 4, func(Job) {
		<-block
	})
	s.Start()

	if err := s.Submit(Job{ID: "slow"}); err != nil {
		t.Fatalf("Submit returned error: %v", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Millisecond)
	defer cancel()

	// The worker is stuck in process; Shutdown must surface ctx error, not hang.
	if err := s.Shutdown(ctx); err != context.DeadlineExceeded {
		t.Fatalf("Shutdown with expiring ctx: got %v, want context.DeadlineExceeded", err)
	}

	// Release the worker so the background drain can finish cleanly.
	close(block)
}

func TestShutdown_Idempotent(t *testing.T) {
	s := NewServer(2, 4, func(Job) {})
	s.Start()

	if err := s.Shutdown(context.Background()); err != nil {
		t.Fatalf("first Shutdown returned error: %v", err)
	}
	if err := s.Shutdown(context.Background()); err != nil {
		t.Fatalf("second Shutdown returned error: %v", err) // must not panic on double close.
	}
}
