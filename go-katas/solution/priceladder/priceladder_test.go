package priceladder

import (
	"sync"
	"testing"
)

func TestAdjustAccumulates(t *testing.T) {
	l := NewLadder()
	l.Adjust("LIV-MUN", 2.00)
	l.Adjust("LIV-MUN", -0.05)
	l.Adjust("LIV-MUN", 0.10)

	got, ok := l.Price("LIV-MUN")
	if !ok {
		t.Fatalf("Price(LIV-MUN) ok = false, want true")
	}
	if want := 2.05; got != want {
		t.Fatalf("Price(LIV-MUN) = %v, want %v", got, want)
	}
}

func TestAdjustAbsentStartsFromZero(t *testing.T) {
	l := NewLadder()
	l.Adjust("NEW", 1.50)

	got, ok := l.Price("NEW")
	if !ok {
		t.Fatalf("Price(NEW) ok = false, want true")
	}
	if want := 1.50; got != want {
		t.Fatalf("Price(NEW) = %v, want %v", got, want)
	}
}

func TestSetOverrides(t *testing.T) {
	l := NewLadder()
	l.Adjust("ARS-CHE", 3.00)
	l.Set("ARS-CHE", 1.80)

	got, ok := l.Price("ARS-CHE")
	if !ok {
		t.Fatalf("Price(ARS-CHE) ok = false, want true")
	}
	if want := 1.80; got != want {
		t.Fatalf("Price(ARS-CHE) = %v, want %v", got, want)
	}
}

func TestPriceMissingMarket(t *testing.T) {
	l := NewLadder()

	got, ok := l.Price("UNKNOWN")
	if ok {
		t.Fatalf("Price(UNKNOWN) ok = true, want false")
	}
	if got != 0 {
		t.Fatalf("Price(UNKNOWN) = %v, want 0", got)
	}
}

// TestConcurrentAdjust_NoLostUpdates fires G goroutines that each apply N
// relative +1 adjustments to the *same* market behind a gated start. If the
// read-modify-write in Adjust is not atomic, concurrent goroutines read the same
// stale value and overwrite each other, so the final price comes out below G*N —
// the lost-update bug. The deltas are integers (1.0) so the expected sum is exact
// in float64 and any shortfall is a lost update, not rounding noise.
func TestConcurrentAdjust_NoLostUpdates(t *testing.T) {
	const (
		goroutines = 16
		perGoro    = 10000
		market     = "HOT-MARKET"
	)

	l := NewLadder()

	var wg sync.WaitGroup
	start := make(chan struct{})

	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < perGoro; i++ {
				l.Adjust(market, 1.0)
			}
		}()
	}

	close(start)
	wg.Wait()

	got, ok := l.Price(market)
	if !ok {
		t.Fatalf("Price(%s) ok = false, want true", market)
	}
	if want := float64(goroutines * perGoro); got != want {
		t.Fatalf("lost updates: Price(%s) = %v, want %v", market, got, want)
	}
}
