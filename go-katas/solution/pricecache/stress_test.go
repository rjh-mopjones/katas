package pricecache

import (
	"fmt"
	"runtime"
	"sync"
	"testing"
)

// encode builds a self-consistent Price from a single value v, so that a reader
// can verify the three fields belong to one write: Bid == v, Ask == v+0.5 and
// Seq == uint64(v). A torn read (fields from different writes) breaks the
// relation and is caught below.
func encode(v uint64) Price {
	return Price{Bid: float64(v), Ask: float64(v) + 0.5, Seq: v}
}

// consistent reports whether p could have been produced by a single encode call.
func consistent(p Price) bool {
	return p.Bid == float64(p.Seq) && p.Ask == float64(p.Seq)+0.5
}

// TestPriceCache_RaceStress drives high contention across many markets with a
// mix of all three public operations — many writers Set, many readers Get, and
// periodic Snapshot — gated to start together. Every writer writes only
// self-consistent prices (see encode), so any Get/Snapshot that observes a torn
// Price (fields from different writes) is a detectable invariant violation. Run
// it under `go test -race -count=N` for the data-race signal; the consistency
// assertions catch struct tearing even without the detector.
func TestPriceCache_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	c := NewPriceCache()

	const (
		markets = 8
		iters   = 20000
	)
	procs := runtime.GOMAXPROCS(0)
	writers := 4 * procs
	readers := 4 * procs
	if writers < 16 {
		writers = 16
	}
	if readers < 16 {
		readers = 16
	}

	marketName := func(i int) string { return fmt.Sprintf("MKT-%d", i%markets) }

	// Seed every market so readers can observe consistent values from the start.
	for i := 0; i < markets; i++ {
		c.Set(marketName(i), encode(uint64(i)))
	}

	var wg sync.WaitGroup
	start := make(chan struct{})

	for w := 0; w < writers; w++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			<-start
			for i := 0; i < iters; i++ {
				v := uint64(id)*uint64(iters) + uint64(i) + 1
				c.Set(marketName(id+i), encode(v))
			}
		}(w)
	}

	for r := 0; r < readers; r++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			<-start
			for i := 0; i < iters; i++ {
				if p, ok := c.Get(marketName(id + i)); ok && !consistent(p) {
					t.Errorf("torn Get: %+v", p)
					return
				}
				if i%64 == 0 {
					for _, p := range c.Snapshot() {
						if !consistent(p) {
							t.Errorf("torn Snapshot entry: %+v", p)
							return
						}
					}
				}
			}
		}(r)
	}

	close(start)
	wg.Wait()
}
