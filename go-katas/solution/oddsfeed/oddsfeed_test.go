package oddsfeed

import (
	"context"
	"runtime"
	"sync/atomic"
	"testing"
	"time"
)

// waitForGoroutines polls runtime.NumGoroutine until it drops to at most want,
// returning the final observed count. The polling is a bounded retry that only
// gives the scheduler time to reap goroutines that are already returning; it does
// not synchronise the logic under test.
func waitForGoroutines(want int) int {
	got := runtime.NumGoroutine()
	for i := 0; i < 100 && got > want; i++ {
		runtime.Gosched()
		time.Sleep(time.Millisecond)
		got = runtime.NumGoroutine()
	}
	return got
}

func TestRun_ProcessesAllMessagesThenReturnsNil(t *testing.T) {
	const n = 1000
	var processed atomic.Int64
	c := NewConsumer(8, func(_ context.Context, _ Message) {
		processed.Add(1)
	})

	in := make(chan Message)
	go func() {
		for i := 0; i < n; i++ {
			in <- Message{Market: "match-1", Payload: "odds"}
		}
		close(in)
	}()

	if err := c.Run(context.Background(), in); err != nil {
		t.Fatalf("Run returned %v, want nil", err)
	}
	if got := processed.Load(); got != n {
		t.Fatalf("handler invoked %d times, want %d", got, n)
	}
}

func TestRun_CancelStopsWorkers(t *testing.T) {
	baseline := runtime.NumGoroutine()

	c := NewConsumer(8, func(_ context.Context, _ Message) {})

	// A channel that is never closed and never fed: workers can only exit via
	// cancellation. The naive loop (no ctx.Done() arm) would leak all 8 here.
	in := make(chan Message)

	ctx, cancel := context.WithCancel(context.Background())
	errCh := make(chan error, 1)
	go func() { errCh <- c.Run(ctx, in) }()

	cancel()

	err := <-errCh
	if err != context.Canceled {
		t.Fatalf("Run returned %v, want context.Canceled", err)
	}

	if got := waitForGoroutines(baseline); got > baseline {
		t.Fatalf("goroutines did not return to baseline: got %d, want <= %d", got, baseline)
	}
}

func TestRun_NoGoroutineLeak(t *testing.T) {
	// Let any goroutines from earlier test phases settle first.
	baseline := waitForGoroutines(runtime.NumGoroutine())

	// Cycle 1: feed then close — workers exit via the channel-closed path.
	runClose := func() {
		c := NewConsumer(16, func(_ context.Context, _ Message) {})
		in := make(chan Message)
		go func() {
			for i := 0; i < 500; i++ {
				in <- Message{Market: "m", Payload: "p"}
			}
			close(in)
		}()
		if err := c.Run(context.Background(), in); err != nil {
			t.Fatalf("Run (close path) returned %v, want nil", err)
		}
	}

	// Cycle 2: cancel a stalled feed — workers exit via the ctx.Done() path.
	runCancel := func() {
		c := NewConsumer(16, func(_ context.Context, _ Message) {})
		in := make(chan Message) // never closed, never fed
		ctx, cancel := context.WithCancel(context.Background())
		errCh := make(chan error, 1)
		go func() { errCh <- c.Run(ctx, in) }()
		cancel()
		if err := <-errCh; err != context.Canceled {
			t.Fatalf("Run (cancel path) returned %v, want context.Canceled", err)
		}
	}

	for i := 0; i < 5; i++ {
		runClose()
		runCancel()
	}

	if got := waitForGoroutines(baseline); got > baseline {
		t.Fatalf("goroutine leak: count %d did not return to baseline %d", got, baseline)
	}
}
