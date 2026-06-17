package aggregator

import (
	"context"
	"fmt"
	"runtime"
	"sync"
	"testing"
	"time"
)

// TestAggregator_RaceStress pushes the Apply / Get / Subscribe+drain+unsubscribe
// races hard under the injected clock, then Closes. This drill historically had a
// close-of-closed-channel bug on the Subscribe/unsubscribe/Close paths, so the test
// is deliberately weighted toward churning subscriptions while Apply and the
// sweeper publish into them and a concurrent Close tears everything down. Run it
// with `go test -race -count=N`.
//
// Assertions:
//
//   - No panic (a double close or send-on-closed would crash the run).
//   - After Close, every subscriber channel is closed (drained to completion).
//   - Goroutine count returns to baseline after Close (the watcher goroutines and
//     the sweeper are all reaped).
//
// The fake clock is advanced concurrently to drive TTL expiry without real sleeps.
func TestAggregator_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	baseline := runtime.NumGoroutine()

	clock := newFakeClock(base())
	a := New(
		WithClock(clock.Now),
		WithTTL(2*time.Second),
		WithSweepInterval(time.Millisecond),
		WithSubscriberBuffer(4),
	)

	const markets = 6
	mkt := func(i int) string { return fmt.Sprintf("m-%d", i) }

	procs := runtime.GOMAXPROCS(0)
	appliers := 8 * procs
	getters := 8 * procs
	subscribers := 8 * procs
	const iters = 300

	start := make(chan struct{})
	var wg sync.WaitGroup

	// Appliers: write back/lay quotes across markets and advance the clock so the
	// sweeper has stale quotes to expire (driving extra publishes into subscribers).
	for w := 0; w < appliers; w++ {
		w := w
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < iters; i++ {
				a.Apply(PriceUpdate{
					Venue:  fmt.Sprintf("v-%d", w%4),
					Market: mkt((w + i) % markets),
					Back:   1.5 + float64((w+i)%50)/100,
					Lay:    1.6 + float64((w+i)%50)/100,
					Ts:     clock.Now(),
				})
				if i%16 == 0 {
					clock.Advance(time.Millisecond)
				}
			}
		}()
	}

	// Getters: hot read path.
	for w := 0; w < getters; w++ {
		w := w
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < iters; i++ {
				_, _ = a.Get(mkt((w + i) % markets))
			}
		}()
	}

	// Subscribers: the contended path. Each repeatedly subscribes, drains a few
	// views, then unsubscribes — racing the sweeper's and appliers' publishes and,
	// eventually, Close. The drain goroutine ranges to completion so a leaked-open
	// channel would hang it (caught by the goroutine-leak check) and a double close
	// would panic.
	for w := 0; w < subscribers; w++ {
		w := w
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < iters/10; i++ {
				ctx := context.Background()
				ch, unsub := a.Subscribe(ctx, mkt((w+i)%markets))
				// Drain a bounded number of views without blocking forever: stop once
				// the channel closes or after a few reads, then unsubscribe.
				reads := 0
				draining := true
				for draining && reads < 8 {
					select {
					case _, ok := <-ch:
						if !ok {
							draining = false
							break
						}
						reads++
					default:
						draining = false
					}
				}
				unsub()
				// Drain to completion so we observe the close (channel ownership is the
				// registry's; unsub closes it exactly once).
				for range ch {
				}
			}
		}()
	}

	close(start)

	// While the storm runs, churn subscriptions from the main goroutine too, then
	// Close concurrently with in-flight Applies/Subscribes. Close must be safe.
	ch, unsub := a.Subscribe(context.Background(), mkt(0))
	_ = unsub
	if err := a.Close(); err != nil {
		t.Fatalf("Close: %v", err)
	}

	// The channel handed out just before Close must be closed by Close (drain it).
	for range ch {
	}

	// A second Close is a no-op (idempotent).
	if err := a.Close(); err != nil {
		t.Fatalf("second Close: %v", err)
	}

	wg.Wait()

	// After Close, a fresh Subscribe returns an already-closed channel.
	post, postUnsub := a.Subscribe(context.Background(), mkt(0))
	postUnsub() // no-op, must not panic
	select {
	case _, ok := <-post:
		if ok {
			t.Error("post-Close Subscribe channel should be closed")
		}
	default:
		t.Error("post-Close Subscribe channel should be closed and readable immediately")
	}

	if !pollUntil(2*time.Second, func() bool { return runtime.NumGoroutine() <= baseline }) {
		t.Errorf("goroutine leak after Close: baseline %d, got %d", baseline, runtime.NumGoroutine())
	}
}
