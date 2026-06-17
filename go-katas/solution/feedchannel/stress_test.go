package feedchannel

import (
	"context"
	"errors"
	"runtime"
	"sync"
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
	for i := 0; i < 200 && got > want; i++ {
		runtime.Gosched()
		time.Sleep(time.Millisecond)
		got = runtime.NumGoroutine()
	}
	return got
}

// TestBroker_RaceStress hammers a single Broker with many concurrent publishers
// (some cancelling their own contexts), an active subscriber ranging Updates(),
// and a Close racing all of them. Run with -race to surface a send-on-closed
// panic, a double-close, or a goroutine leak.
func TestBroker_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	baseline := runtime.NumGoroutine()

	const (
		publishers = 48
		iterations = 400
	)

	b := NewBroker(16)

	// One subscriber drains the feed; its range loop must terminate when Close
	// closes the updates channel.
	var received atomic.Int64
	subDone := make(chan struct{})
	go func() {
		defer close(subDone)
		for range b.Updates() {
			received.Add(1)
		}
	}()

	start := make(chan struct{})
	var wg sync.WaitGroup
	wg.Add(publishers)

	var unexpected atomic.Int64 // non-nil, non-ErrClosed, non-ctx errors

	for p := 0; p < publishers; p++ {
		go func(p int) {
			defer wg.Done()
			<-start
			for i := 0; i < iterations; i++ {
				ctx := context.Background()
				var cancel context.CancelFunc
				// Some publishers use a context they cancel mid-flight, exercising
				// the ctx.Done() arm of Publish's select under contention.
				if i%5 == 0 {
					ctx, cancel = context.WithCancel(context.Background())
					cancel()
				}
				err := b.Publish(ctx, Update{Market: "AAPL", Price: float64(i)})
				switch {
				case err == nil:
				case errors.Is(err, ErrClosed):
				case errors.Is(err, context.Canceled):
				default:
					unexpected.Add(1)
				}
				if cancel != nil {
					cancel()
				}
			}
		}(p)
	}

	// Closer races the active publishers: it closes the broker while sends are
	// in flight. A correct Broker neither panics nor double-closes.
	closer := make(chan struct{})
	go func() {
		defer close(closer)
		<-start
		// Let some traffic flow first so Close genuinely races live publishers.
		for i := 0; i < 50; i++ {
			runtime.Gosched()
		}
		b.Close()
		b.Close() // idempotent under concurrency.
	}()

	close(start)
	wg.Wait()
	<-closer

	// Every Publish has returned and the broker is closed; the subscriber's range
	// must now terminate.
	<-subDone

	if n := unexpected.Load(); n != 0 {
		t.Fatalf("Publish returned %d unexpected errors (want only nil/ErrClosed/ctx)", n)
	}

	// Publish after Close must return ErrClosed, never panic.
	if err := b.Publish(context.Background(), Update{Market: "MSFT", Price: 1}); !errors.Is(err, ErrClosed) {
		t.Fatalf("Publish after Close: got %v, want ErrClosed", err)
	}

	if got := waitForGoroutines(baseline); got > baseline {
		t.Fatalf("goroutine leak: started near %d, still %d after stress", baseline, got)
	}
}
