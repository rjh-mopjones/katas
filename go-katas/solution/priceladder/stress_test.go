package priceladder

import (
	"fmt"
	"runtime"
	"sync"
	"testing"
)

// TestPriceLadder_RaceStress drives high contention across several markets with
// a mix of all three public operations, gated to start together. One market
// ("ADJUST-ONLY") is touched ONLY by +1 adjusters, so its final price must equal
// the total number of adjustments — any shortfall is a lost update (the RMW was
// not atomic). The remaining markets mix Adjust/Set/Price so the detector
// interleaves every method; we rely on `-race` plus no panic there. Run under
// `go test -race -count=N`.
func TestPriceLadder_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	l := NewLadder()

	const (
		adjustOnly = "ADJUST-ONLY"
		mixed      = 8
		iters      = 20000
	)
	procs := runtime.GOMAXPROCS(0)
	adjusters := 4 * procs
	if adjusters < 16 {
		adjusters = 16
	}
	mixers := 2 * procs
	if mixers < 8 {
		mixers = 8
	}

	mixedName := func(i int) string { return fmt.Sprintf("MIX-%d", i%mixed) }

	var wg sync.WaitGroup
	start := make(chan struct{})

	// Lost-update probes: every iteration adds exactly +1 to the same market.
	for a := 0; a < adjusters; a++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < iters; i++ {
				l.Adjust(adjustOnly, 1.0)
			}
		}()
	}

	// Mixers exercise Adjust/Set/Price on the other markets concurrently.
	for x := 0; x < mixers; x++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			<-start
			for i := 0; i < iters; i++ {
				name := mixedName(id + i)
				switch i % 3 {
				case 0:
					l.Adjust(name, 0.5)
				case 1:
					l.Set(name, float64(i))
				default:
					_, _ = l.Price(name)
				}
			}
		}(x)
	}

	close(start)
	wg.Wait()

	got, ok := l.Price(adjustOnly)
	if !ok {
		t.Fatalf("Price(%s) ok = false, want true", adjustOnly)
	}
	if want := float64(adjusters * iters); got != want {
		t.Fatalf("lost updates: Price(%s) = %v, want %v", adjustOnly, got, want)
	}
}
