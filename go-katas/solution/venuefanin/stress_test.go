package venuefanin

import (
	"context"
	"runtime"
	"sync"
	"testing"
)

// TestFanIn_RaceStress merges many producer-fed source channels under the race
// detector across two sub-scenarios: a clean drain (no drop, no dup, output
// closes) and a mid-stream context cancellation (output closes, no leak). Note:
// waitForGoroutines is defined in venuefanin_test.go.
func TestFanIn_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	t.Run("clean_drain_no_drop_no_dup", func(t *testing.T) {
		baseline := runtime.NumGoroutine()

		const (
			venues = 48
			perSrc = 300
		)

		sources := make([]<-chan Quote, venues)
		start := make(chan struct{})
		var prod sync.WaitGroup
		prod.Add(venues)
		for v := 0; v < venues; v++ {
			ch := make(chan Quote)
			sources[v] = ch
			go func(v int, ch chan<- Quote) {
				defer prod.Done()
				<-start
				for j := 0; j < perSrc; j++ {
					ch <- Quote{Venue: venueName(v), Market: "AAPL", Price: float64(j)}
				}
				close(ch)
			}(v, ch)
		}

		// Small buffer relative to venues*perSrc forces real backpressure.
		out := FanIn(context.Background(), 8, sources...)

		close(start)

		got := make(map[Quote]int)
		total := 0
		for q := range out { // terminates only when the closer closes out.
			got[q]++
			total++
		}
		prod.Wait()

		if want := venues * perSrc; total != want {
			t.Fatalf("total quotes: got %d, want %d", total, want)
		}
		for v := 0; v < venues; v++ {
			for j := 0; j < perSrc; j++ {
				q := Quote{Venue: venueName(v), Market: "AAPL", Price: float64(j)}
				if c := got[q]; c != 1 {
					t.Fatalf("quote %v appeared %d times, want exactly 1 (drop or dup)", q, c)
				}
			}
		}

		if g := waitForGoroutines(baseline); g > baseline {
			t.Fatalf("goroutine leak: started near %d, still %d", baseline, g)
		}
	})

	t.Run("cancel_midstream_closes_and_no_leak", func(t *testing.T) {
		baseline := runtime.NumGoroutine()

		const venues = 48

		ctx, cancel := context.WithCancel(context.Background())
		defer cancel()

		sources := make([]<-chan Quote, venues)
		start := make(chan struct{})
		// Producers emit indefinitely until their (unbuffered) send is abandoned
		// by the forwarder returning on ctx cancellation; stop closes them so the
		// producers themselves can exit cleanly after the test.
		stop := make(chan struct{})
		var prod sync.WaitGroup
		prod.Add(venues)
		for v := 0; v < venues; v++ {
			ch := make(chan Quote)
			sources[v] = ch
			go func(v int, ch chan Quote) {
				defer prod.Done()
				<-start
				for j := 0; ; j++ {
					select {
					case ch <- Quote{Venue: venueName(v), Market: "AAPL", Price: float64(j)}:
					case <-stop:
						return
					}
				}
			}(v, ch)
		}

		out := FanIn(ctx, 8, sources...)
		close(start)

		// Drain a bounded prefix, then cancel mid-stream.
		for i := 0; i < 2000; i++ {
			<-out
		}
		cancel()

		// The output must close once every forwarder observes the cancellation —
		// the range terminates without our help.
		for range out {
		}

		close(stop) // release the still-blocked producers.
		prod.Wait()

		if g := waitForGoroutines(baseline); g > baseline {
			t.Fatalf("goroutine leak after cancel: started near %d, still %d", baseline, g)
		}
	})
}

func venueName(v int) string {
	// Distinct, stable per-index venue identity for the multiset check.
	return "V" + string(rune('A'+v%26)) + string(rune('0'+v/26))
}
