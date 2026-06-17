package pricecache

import (
	"sync"
	"testing"
)

func TestSetThenGet(t *testing.T) {
	c := NewPriceCache()
	want := Price{Bid: 1.95, Ask: 2.05, Seq: 1}
	c.Set("LIV-MUN", want)

	got, ok := c.Get("LIV-MUN")
	if !ok {
		t.Fatalf("Get(LIV-MUN) ok = false, want true")
	}
	if got != want {
		t.Fatalf("Get(LIV-MUN) = %+v, want %+v", got, want)
	}
}

func TestGetMissingKey(t *testing.T) {
	c := NewPriceCache()

	got, ok := c.Get("UNKNOWN")
	if ok {
		t.Fatalf("Get(UNKNOWN) ok = true, want false")
	}
	if got != (Price{}) {
		t.Fatalf("Get(UNKNOWN) = %+v, want zero Price", got)
	}
}

func TestSetOverwrites(t *testing.T) {
	c := NewPriceCache()
	c.Set("ARS-CHE", Price{Bid: 1.50, Ask: 1.60, Seq: 1})
	c.Set("ARS-CHE", Price{Bid: 1.55, Ask: 1.65, Seq: 2})

	got, ok := c.Get("ARS-CHE")
	if !ok {
		t.Fatalf("Get(ARS-CHE) ok = false, want true")
	}
	want := Price{Bid: 1.55, Ask: 1.65, Seq: 2}
	if got != want {
		t.Fatalf("Get(ARS-CHE) = %+v, want %+v", got, want)
	}
}

func TestSeqRoundTrips(t *testing.T) {
	c := NewPriceCache()
	for seq := uint64(0); seq < 100; seq++ {
		c.Set("MKT", Price{Bid: float64(seq), Ask: float64(seq) + 0.1, Seq: seq})
		got, _ := c.Get("MKT")
		if got.Seq != seq {
			t.Fatalf("after Set seq=%d, Get Seq = %d", seq, got.Seq)
		}
		if got.Bid != float64(seq) || got.Ask != float64(seq)+0.1 {
			t.Fatalf("torn price at seq=%d: got %+v", seq, got)
		}
	}
}

func TestSnapshotIsACopy(t *testing.T) {
	c := NewPriceCache()
	c.Set("A", Price{Bid: 1, Ask: 2, Seq: 1})
	c.Set("B", Price{Bid: 3, Ask: 4, Seq: 1})

	snap := c.Snapshot()
	if len(snap) != 2 {
		t.Fatalf("Snapshot len = %d, want 2", len(snap))
	}

	// Mutating the returned map must not affect the cache.
	snap["A"] = Price{Bid: 99, Ask: 99, Seq: 99}
	delete(snap, "B")
	snap["C"] = Price{Bid: 5, Ask: 6, Seq: 1}

	if got, _ := c.Get("A"); got != (Price{Bid: 1, Ask: 2, Seq: 1}) {
		t.Fatalf("cache A mutated via snapshot: %+v", got)
	}
	if _, ok := c.Get("B"); !ok {
		t.Fatalf("cache B deleted via snapshot")
	}
	if _, ok := c.Get("C"); ok {
		t.Fatalf("cache C inserted via snapshot")
	}
}

// TestConcurrentReadWrite_NoRace runs one writer and many readers against the
// cache with a gated start to maximise contention. Under `go test -race` this is
// the test that would flag a naive map-without-sync implementation; the correct
// RWMutex implementation must pass cleanly.
func TestConcurrentReadWrite_NoRace(t *testing.T) {
	c := NewPriceCache()
	const market = "HOT-MARKET"
	c.Set(market, Price{Bid: 1, Ask: 2, Seq: 0})

	const (
		readers      = 16
		writes       = 5000
		readsPerGoro = 5000
	)

	var wg sync.WaitGroup
	start := make(chan struct{})

	wg.Add(1)
	go func() {
		defer wg.Done()
		<-start
		for seq := uint64(1); seq <= writes; seq++ {
			c.Set(market, Price{Bid: float64(seq), Ask: float64(seq) + 0.1, Seq: seq})
		}
	}()

	for r := 0; r < readers; r++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < readsPerGoro; i++ {
				p, ok := c.Get(market)
				if !ok {
					continue
				}
				// Assert no torn read: the three fields must agree, i.e. the
				// price observed is a value some single Set actually wrote.
				if p.Seq != 0 && (p.Bid != float64(p.Seq) || p.Ask != float64(p.Seq)+0.1) {
					t.Errorf("torn read: %+v", p)
					return
				}
				_ = c.Snapshot()
			}
		}()
	}

	close(start)
	wg.Wait()

	got, ok := c.Get(market)
	if !ok || got.Seq != writes {
		t.Fatalf("after writes: got %+v ok=%v, want Seq=%d", got, ok, writes)
	}
}
