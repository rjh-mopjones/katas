package matchingengine

import (
	"context"
	"fmt"
	"runtime"
	"sync"
	"testing"
	"time"
)

// TestEngine_RaceStress hammers a single Engine with many goroutines submitting a
// mix of buy/sell, limit/market orders of varying price and quantity, all gated to
// start at once for maximum contention. It is the conservation-invariant test under
// the race detector: run it with `go test -race -count=N`.
//
// The single invariant that must hold no matter how submissions interleave is
// QUANTITY CONSERVATION: every unit that fills the buy side fills the sell side
// too, so across ALL trades returned by ALL submissions, total buy-side filled
// quantity == total sell-side filled quantity, and no order ever fills beyond its
// own quantity. The matching loop is the single writer, so the outcome depends only
// on submission order — but the detector still proves the channel hand-off, the
// reply path, and Close are race-free.
func TestEngine_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	baseline := runtime.NumGoroutine()

	e := NewEngine()
	e.Start()

	const iters = 400
	workers := 16 * runtime.GOMAXPROCS(0)
	if workers < 32 {
		workers = 32
	}

	// Per-order accounting, gathered per goroutine then merged, so the test itself
	// adds no shared mutable state to race on.
	type tally struct {
		// filledByOrder maps an order id to the total quantity it filled (as taker
		// or maker). Used to assert no order over-fills.
		filledByOrder map[string]int64
		// buyFilled/sellFilled are the totals attributable to buy/sell orders. We
		// classify each trade by the SIDE of its taker and maker: a Trade always has
		// one taker and one resting maker on the opposite side, so every traded unit
		// is counted once on the buy side and once on the sell side.
		buyFilled, sellFilled int64
		// qty records each order id's original quantity so the merge step can check
		// the per-order over-fill cap.
		qty map[string]int64
	}

	start := make(chan struct{})
	var wg sync.WaitGroup
	tallies := make([]tally, workers)

	for w := 0; w < workers; w++ {
		w := w
		tallies[w] = tally{
			filledByOrder: make(map[string]int64),
			qty:           make(map[string]int64),
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			ctx := context.Background()
			tl := &tallies[w]
			<-start
			for i := 0; i < iters; i++ {
				// Deterministic-but-varied order parameters from (w, i): price near a
				// common midpoint so buys and sells actually cross, qty 1..5, a mix
				// of sides and types so resting liquidity builds and is swept.
				id := fmt.Sprintf("o-%d-%d", w, i)
				side := Buy
				if (w+i)%2 == 0 {
					side = Sell
				}
				typ := Limit
				if i%7 == 0 {
					typ = Market
				}
				price := int64(100 + (i%9 - 4)) // 96..104, straddling 100
				qty := int64(1 + (w+i)%5)       // 1..5

				o := Order{ID: id, Side: side, Type: typ, Price: price, Qty: qty}
				tl.qty[id] = qty

				trades, err := e.Submit(ctx, o)
				if err != nil {
					t.Errorf("Submit %s: %v", id, err)
					return
				}
				for _, tr := range trades {
					tl.filledByOrder[tr.TakerID] += tr.Qty
					tl.filledByOrder[tr.MakerID] += tr.Qty
					// Every trade pairs one taker with one resting maker on the
					// opposite side, so each traded unit counts once on each side.
					tl.buyFilled += tr.Qty
					tl.sellFilled += tr.Qty
				}
			}
		}()
	}

	close(start)
	wg.Wait()
	e.Close()

	// Merge per-goroutine tallies. buyFilled and sellFilled are incremented in
	// lock-step on every trade (one taker + one maker), so their global sums must be
	// equal: that IS conservation of quantity across the whole run.
	var totalBuy, totalSell int64
	filled := make(map[string]int64)
	origQty := make(map[string]int64)
	for w := range tallies {
		totalBuy += tallies[w].buyFilled
		totalSell += tallies[w].sellFilled
		for id, q := range tallies[w].filledByOrder {
			filled[id] += q
		}
		for id, q := range tallies[w].qty {
			origQty[id] = q
		}
	}

	if totalBuy != totalSell {
		t.Fatalf("quantity not conserved: buy-side filled %d, sell-side filled %d", totalBuy, totalSell)
	}

	// No order may fill beyond its own original quantity.
	for id, f := range filled {
		if oq, ok := origQty[id]; ok && f > oq {
			t.Fatalf("order %s over-filled: filled %d > qty %d", id, f, oq)
		}
	}

	if got := waitForGoroutinesME(baseline); got > baseline {
		t.Errorf("goroutine leak after Close: baseline %d, got %d", baseline, got)
	}
}

// waitForGoroutinesME polls runtime.NumGoroutine until it drops to at most want,
// returning the final observed count. The bounded poll only gives the scheduler
// time to reap goroutines that are already returning; it does not synchronise the
// logic under test.
func waitForGoroutinesME(want int) int {
	got := runtime.NumGoroutine()
	for i := 0; i < 200 && got > want; i++ {
		runtime.Gosched()
		time.Sleep(time.Millisecond)
		got = runtime.NumGoroutine()
	}
	return got
}
