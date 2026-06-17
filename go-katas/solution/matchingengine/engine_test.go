package matchingengine

import (
	"context"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
)

func TestEngine_SerialisedMatching(t *testing.T) {
	e := NewEngine()
	e.Start()
	defer e.Close()
	ctx := context.Background()

	if _, err := e.Submit(ctx, Order{ID: "a1", Side: Sell, Type: Limit, Price: 100, Qty: 10}); err != nil {
		t.Fatalf("Submit maker: %v", err)
	}
	trades, err := e.Submit(ctx, Order{ID: "b1", Side: Buy, Type: Limit, Price: 100, Qty: 4})
	if err != nil {
		t.Fatalf("Submit taker: %v", err)
	}
	if len(trades) != 1 || trades[0].Price != 100 || trades[0].Qty != 4 {
		t.Fatalf("trades = %+v, want one fill of 4 @100", trades)
	}
}

func TestEngine_SubmitAfterClose(t *testing.T) {
	e := NewEngine()
	e.Start()
	e.Close()

	if _, err := e.Submit(context.Background(), Order{ID: "x", Side: Buy, Type: Limit, Price: 1, Qty: 1}); err != ErrEngineClosed {
		t.Fatalf("Submit after Close = %v, want ErrEngineClosed", err)
	}
}

func TestEngine_CloseIdempotentAndNoLeak(t *testing.T) {
	before := runtime.NumGoroutine()

	e := NewEngine()
	e.Start()
	e.Close()
	e.Close() // idempotent: must not panic on double close.

	waitForGoroutines(t, before)
}

// TestEngine_ConcurrentSubmit_DeterministicAggregate fires many goroutines that
// each Submit concurrently behind a gated start. The book is seeded with a known
// quantity of resting liquidity on one side; the goroutines submit aggressive
// orders that cross it. Because the matching loop serialises every order, the
// TOTAL filled quantity is deterministic regardless of interleaving: it is
// exactly the seeded resting quantity (the takers collectively want more than the
// book holds, so all resting liquidity is consumed and no more). This is the
// aggregate the test pins, and it must be race-clean under `go test -race`.
func TestEngine_ConcurrentSubmit_DeterministicAggregate(t *testing.T) {
	e := NewEngine()
	e.Start()
	defer e.Close()
	ctx := context.Background()

	// Seed: 100 resting sell units across several price levels, all <= 110.
	const restingUnits = 100
	for i := 0; i < 10; i++ {
		if _, err := e.Submit(ctx, Order{ID: "rest" + orderID(i), Side: Sell, Type: Limit, Price: int64(100 + i), Qty: 10}); err != nil {
			t.Fatalf("seed: %v", err)
		}
	}

	const submitters = 32
	const qtyEach = 10 // total demand 320 >> 100 resting -> book fully consumed.

	var totalFilled atomic.Int64
	var wg sync.WaitGroup
	start := make(chan struct{})

	for g := 0; g < submitters; g++ {
		wg.Add(1)
		go func(g int) {
			defer wg.Done()
			<-start
			// Buy limit at 110 crosses every seeded level.
			trades, err := e.Submit(ctx, Order{ID: "t" + orderID(g), Side: Buy, Type: Limit, Price: 110, Qty: qtyEach})
			if err != nil {
				t.Errorf("Submit: %v", err)
				return
			}
			var f int64
			for _, tr := range trades {
				f += tr.Qty
			}
			totalFilled.Add(f)
		}(g)
	}

	close(start)
	wg.Wait()

	if got := totalFilled.Load(); got != restingUnits {
		t.Fatalf("total filled = %d, want %d (all resting liquidity, no more)", got, restingUnits)
	}
}

// TestEngine_CloseWithActiveSubmitters closes the engine while many goroutines
// are still submitting. Every Submit must return cleanly — either trades+nil or
// ErrEngineClosed — with no panic (send on closed channel) and no goroutine leak.
func TestEngine_CloseWithActiveSubmitters(t *testing.T) {
	before := runtime.NumGoroutine()

	e := NewEngine()
	e.Start()
	ctx := context.Background()

	const submitters = 32
	var wg sync.WaitGroup
	start := make(chan struct{})

	for g := 0; g < submitters; g++ {
		wg.Add(1)
		go func(g int) {
			defer wg.Done()
			<-start
			for i := 0; i < 50; i++ {
				_, err := e.Submit(ctx, Order{ID: "x", Side: Buy, Type: Limit, Price: 100, Qty: 1})
				if err != nil && err != ErrEngineClosed {
					t.Errorf("Submit returned unexpected error: %v", err)
					return
				}
			}
		}(g)
	}

	close(start)
	e.Close() // close concurrently with the active submitters.
	wg.Wait()

	waitForGoroutines(t, before)
}

// waitForGoroutines polls (bounded) until the goroutine count returns to within
// the baseline, allowing the runtime's bookkeeping goroutines to settle. This is
// the only permitted poll — it asserts no leak without a real sleep.
func waitForGoroutines(t *testing.T, baseline int) {
	t.Helper()
	for i := 0; i < 1000; i++ {
		if runtime.NumGoroutine() <= baseline {
			return
		}
		runtime.Gosched()
	}
	t.Fatalf("goroutine leak: %d goroutines, baseline %d", runtime.NumGoroutine(), baseline)
}
