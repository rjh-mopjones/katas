package venuefanin

import (
	"context"
	"runtime"
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

func TestFanIn_MergesAllSourcesNoDropNoDup(t *testing.T) {
	const k = 500
	venues := []string{"NYSE", "LSE", "TSE"}

	sources := make([]<-chan Quote, len(venues))
	for i, v := range venues {
		ch := make(chan Quote)
		sources[i] = ch
		go func(v string, ch chan<- Quote) {
			for j := 0; j < k; j++ {
				ch <- Quote{Venue: v, Market: "AAPL", Price: float64(j)}
			}
			close(ch)
		}(v, ch)
	}

	// Small buffer relative to 3*k forces real backpressure during the merge.
	out := FanIn(context.Background(), 4, sources...)

	got := make(map[Quote]int)
	total := 0
	for q := range out {
		got[q]++
		total++
	}

	if want := len(venues) * k; total != want {
		t.Fatalf("total quotes: got %d, want %d", total, want)
	}

	// Multiset check: every (venue, price) pair must appear exactly once — no
	// drops (count 0) and no duplicates (count > 1).
	for _, v := range venues {
		for j := 0; j < k; j++ {
			q := Quote{Venue: v, Market: "AAPL", Price: float64(j)}
			if c := got[q]; c != 1 {
				t.Fatalf("quote %v appeared %d times, want exactly 1", q, c)
			}
		}
	}
}

func TestFanIn_CancelStops(t *testing.T) {
	baseline := runtime.NumGoroutine()

	// Sources that emit forever and never close — only ctx cancel can stop FanIn.
	// producerDone lets the test's own producer goroutines exit afterward so they
	// don't taint the leak check.
	const nSources = 3
	ctx, cancel := context.WithCancel(context.Background())
	producerDone := make(chan struct{})
	sources := make([]<-chan Quote, nSources)
	for i := 0; i < nSources; i++ {
		ch := make(chan Quote)
		sources[i] = ch
		go func(ch chan<- Quote) {
			for {
				select {
				case ch <- Quote{Venue: "X", Market: "AAPL", Price: 1}:
				case <-producerDone:
					return
				}
			}
		}(ch)
	}

	out := FanIn(ctx, 2, sources...)

	// Drain a few to prove it is live, then cancel.
	for i := 0; i < 10; i++ {
		<-out
	}
	cancel()

	// The output channel must close once the forwarders observe cancellation.
	for range out {
		// drain whatever is still buffered/in-flight until close.
	}

	close(producerDone) // stop the test's own producer goroutines.

	if got := waitForGoroutines(baseline); got > baseline {
		t.Fatalf("goroutine leak: baseline %d, after cancel %d", baseline, got)
	}
}

func TestFanIn_ClosesWhenAllSourcesClose(t *testing.T) {
	const nSources = 4
	sources := make([]<-chan Quote, nSources)
	for i := 0; i < nSources; i++ {
		ch := make(chan Quote)
		sources[i] = ch
		go func(ch chan<- Quote) {
			ch <- Quote{Venue: "Y", Market: "GOOG", Price: 2}
			close(ch)
		}(ch)
	}

	out := FanIn(context.Background(), 1, sources...)

	count := 0
	for range out {
		count++
	}

	// range terminating proves the output closed without any cancellation.
	if count != nSources {
		t.Fatalf("received %d quotes, want %d", count, nSources)
	}
}
