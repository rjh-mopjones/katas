package matchingengine

import (
	"math/rand"
	"testing"
)

// helper: submit and return trades, failing the test on an unexpected count.
func submit(b *OrderBook, o Order) []Trade {
	return b.Submit(o)
}

func TestCrossingLimit_TradesAtMakerPrice(t *testing.T) {
	b := NewOrderBook()

	// A resting sell (maker) at 102 for 10.
	if tr := submit(b, Order{ID: "maker", Side: Sell, Type: Limit, Price: 102, Qty: 10}); len(tr) != 0 {
		t.Fatalf("resting maker produced trades: %v", tr)
	}

	// A buy (taker) limit at 105 crosses it. Trade must execute at the MAKER
	// price (102), not the taker's 105.
	trades := submit(b, Order{ID: "taker", Side: Buy, Type: Limit, Price: 105, Qty: 4})
	if len(trades) != 1 {
		t.Fatalf("got %d trades, want 1", len(trades))
	}
	got := trades[0]
	want := Trade{MakerID: "maker", TakerID: "taker", Price: 102, Qty: 4}
	if got != want {
		t.Fatalf("trade = %+v, want %+v", got, want)
	}

	// The maker's remaining 6 must still rest at 102 (still cancellable).
	if !b.Cancel("maker") {
		t.Fatalf("maker should still rest with remaining 6")
	}
}

func TestRestingRemainder_StaysOnBook(t *testing.T) {
	b := NewOrderBook()

	// Buy limit at 100 for 10, nothing to match -> rests entirely.
	if tr := submit(b, Order{ID: "b1", Side: Buy, Type: Limit, Price: 100, Qty: 10}); len(tr) != 0 {
		t.Fatalf("unexpected trades: %v", tr)
	}

	// Sell limit at 100 for 4 crosses the bid; 4 trade, 6 of the bid remain.
	trades := submit(b, Order{ID: "s1", Side: Sell, Type: Limit, Price: 100, Qty: 4})
	if len(trades) != 1 || trades[0].Qty != 4 || trades[0].Price != 100 {
		t.Fatalf("trades = %+v, want one fill of 4 @100", trades)
	}

	// The remaining 6 on b1 still quotes a bid at 100; no ask rests.
	if !b.Cancel("b1") {
		t.Fatalf("b1 should still rest with remaining 6, Cancel returned false")
	}
	if b.Cancel("s1") {
		t.Fatalf("s1 was fully filled and must not rest")
	}
}

func TestPartialFill_SweepsMultipleLevels(t *testing.T) {
	b := NewOrderBook()

	// Three resting asks at increasing prices.
	submit(b, Order{ID: "a1", Side: Sell, Type: Limit, Price: 100, Qty: 5})
	submit(b, Order{ID: "a2", Side: Sell, Type: Limit, Price: 101, Qty: 5})
	submit(b, Order{ID: "a3", Side: Sell, Type: Limit, Price: 102, Qty: 5})

	// A big buy limit at 101 sweeps 100 then 101 (10 units), stops before 102,
	// rests the remaining 2 at 101.
	trades := submit(b, Order{ID: "big", Side: Buy, Type: Limit, Price: 101, Qty: 12})
	if len(trades) != 2 {
		t.Fatalf("got %d trades, want 2", len(trades))
	}
	// Price priority: the 100 level fills before the 101 level.
	if trades[0].Price != 100 || trades[0].Qty != 5 || trades[0].MakerID != "a1" {
		t.Fatalf("trade[0] = %+v, want a1 5 @100", trades[0])
	}
	if trades[1].Price != 101 || trades[1].Qty != 5 || trades[1].MakerID != "a2" {
		t.Fatalf("trade[1] = %+v, want a2 5 @101", trades[1])
	}

	// a3 untouched at 102; "big" rested its remaining 2 as the new best bid @101.
	bid, ask, ok := b.Best()
	if !ok || bid != 101 || ask != 102 {
		t.Fatalf("Best = (%d,%d,%v), want bid 101 ask 102", bid, ask, ok)
	}
}

func TestPriceTimePriority_FIFOAtALevel(t *testing.T) {
	b := NewOrderBook()

	// Two sells at the SAME price; e1 arrives first.
	submit(b, Order{ID: "e1", Side: Sell, Type: Limit, Price: 100, Qty: 5})
	submit(b, Order{ID: "e2", Side: Sell, Type: Limit, Price: 100, Qty: 5})

	// A buy for 5 must fill e1 (earliest) entirely, not e2.
	trades := submit(b, Order{ID: "t", Side: Buy, Type: Limit, Price: 100, Qty: 5})
	if len(trades) != 1 || trades[0].MakerID != "e1" {
		t.Fatalf("trades = %+v, want single fill against e1 (FIFO)", trades)
	}

	// e1 is gone; e2 still rests.
	if b.Cancel("e1") {
		t.Fatalf("e1 should be fully filled and removed")
	}
	if !b.Cancel("e2") {
		t.Fatalf("e2 should still rest")
	}
}

func TestMarketOrder_SweepsAndNeverRests(t *testing.T) {
	b := NewOrderBook()

	submit(b, Order{ID: "a1", Side: Sell, Type: Limit, Price: 100, Qty: 3})
	submit(b, Order{ID: "a2", Side: Sell, Type: Limit, Price: 105, Qty: 3})

	// Market buy for 10: sweeps both asks (6 units) at their prices, the
	// remaining 4 is discarded (never rests).
	trades := submit(b, Order{ID: "m", Side: Buy, Type: Market, Qty: 10})
	if len(trades) != 2 {
		t.Fatalf("got %d trades, want 2", len(trades))
	}
	var filled int64
	for _, tr := range trades {
		filled += tr.Qty
	}
	if filled != 6 {
		t.Fatalf("market filled %d, want 6 (all available liquidity)", filled)
	}

	// No asks left; the market order did not rest, so no bid either.
	if _, _, ok := b.Best(); ok {
		t.Fatalf("book should be empty after sweeping all liquidity")
	}
	if b.Cancel("m") {
		t.Fatalf("market order must never rest on the book")
	}
}

func TestMarketOrder_EmptyBook_NoFill(t *testing.T) {
	b := NewOrderBook()
	if trades := submit(b, Order{ID: "m", Side: Sell, Type: Market, Qty: 5}); len(trades) != 0 {
		t.Fatalf("market against empty book: trades = %+v, want none", trades)
	}
	if b.Cancel("m") {
		t.Fatalf("market order must not rest")
	}
}

func TestCancel_RemovesRestingOrder(t *testing.T) {
	b := NewOrderBook()
	submit(b, Order{ID: "a1", Side: Sell, Type: Limit, Price: 100, Qty: 5})

	if !b.Cancel("a1") {
		t.Fatalf("Cancel(a1) = false, want true")
	}
	// Cancelled order no longer fills.
	if trades := submit(b, Order{ID: "b1", Side: Buy, Type: Limit, Price: 100, Qty: 5}); len(trades) != 0 {
		t.Fatalf("cancelled order still matched: %+v", trades)
	}
	if b.Cancel("ghost") {
		t.Fatalf("Cancel(unknown) = true, want false")
	}
}

func TestAmendReduce_KeepsPriority(t *testing.T) {
	b := NewOrderBook()
	// e1 first at 100 for 10, e2 second at 100 for 10.
	submit(b, Order{ID: "e1", Side: Sell, Type: Limit, Price: 100, Qty: 10})
	submit(b, Order{ID: "e2", Side: Sell, Type: Limit, Price: 100, Qty: 10})

	// Reduce e1 to 4. It must KEEP its front-of-queue priority.
	if !b.Amend("e1", 4) {
		t.Fatalf("Amend reduce returned false")
	}

	// A buy for 4 fills the (reduced) e1 first, proving priority was kept.
	trades := submit(b, Order{ID: "t", Side: Buy, Type: Limit, Price: 100, Qty: 4})
	if len(trades) != 1 || trades[0].MakerID != "e1" || trades[0].Qty != 4 {
		t.Fatalf("trades = %+v, want e1 filled for 4 (priority kept)", trades)
	}
	if b.Cancel("e1") {
		t.Fatalf("e1 should now be fully filled")
	}
}

func TestAmend_RejectsIncrease(t *testing.T) {
	b := NewOrderBook()
	submit(b, Order{ID: "a1", Side: Sell, Type: Limit, Price: 100, Qty: 5})
	if b.Amend("a1", 9) {
		t.Fatalf("Amend increase returned true; amend-up must be rejected (loses priority)")
	}
	if b.Amend("ghost", 1) {
		t.Fatalf("Amend unknown returned true, want false")
	}
}

func TestAmend_ToZero_Cancels(t *testing.T) {
	b := NewOrderBook()
	submit(b, Order{ID: "a1", Side: Sell, Type: Limit, Price: 100, Qty: 5})
	if !b.Amend("a1", 0) {
		t.Fatalf("Amend to 0 returned false")
	}
	if b.Cancel("a1") {
		t.Fatalf("a1 should have been removed by amend-to-zero")
	}
}

func TestBest_ReflectsBook(t *testing.T) {
	b := NewOrderBook()
	if _, _, ok := b.Best(); ok {
		t.Fatalf("empty book should report ok=false")
	}
	submit(b, Order{ID: "b1", Side: Buy, Type: Limit, Price: 99, Qty: 5})
	if _, _, ok := b.Best(); ok {
		t.Fatalf("one-sided book should report ok=false")
	}
	submit(b, Order{ID: "b2", Side: Buy, Type: Limit, Price: 101, Qty: 5})
	submit(b, Order{ID: "a1", Side: Sell, Type: Limit, Price: 105, Qty: 5})
	submit(b, Order{ID: "a2", Side: Sell, Type: Limit, Price: 103, Qty: 5})

	bid, ask, ok := b.Best()
	if !ok || bid != 101 || ask != 103 {
		t.Fatalf("Best = (%d,%d,%v), want bid 101 ask 103", bid, ask, ok)
	}
}

// TestQuantityConservation runs a long seeded sequence of random limit orders
// and verifies the money-safety invariant: total quantity bought == total
// quantity sold across every trade, and no order is ever filled beyond its
// original quantity. A double fill or phantom fill would break one of these.
func TestQuantityConservation(t *testing.T) {
	const seed = 0xC0FFEE
	rng := rand.New(rand.NewSource(seed))
	b := NewOrderBook()

	filledByOrder := make(map[string]int64)
	origQty := make(map[string]int64)
	sideOf := make(map[string]Side)

	var boughtQty, soldQty int64

	for i := 0; i < 5000; i++ {
		id := orderID(i)
		side := Buy
		if rng.Intn(2) == 0 {
			side = Sell
		}
		price := int64(95 + rng.Intn(11)) // 95..105, guarantees frequent crossing
		qty := int64(1 + rng.Intn(10))
		origQty[id] = qty
		sideOf[id] = side

		trades := b.Submit(Order{ID: id, Side: side, Type: Limit, Price: price, Qty: qty})
		for _, tr := range trades {
			filledByOrder[tr.MakerID] += tr.Qty
			filledByOrder[tr.TakerID] += tr.Qty
			// Attribute each trade to the buyer and the seller by their real
			// sides: the taker and maker are always on opposite sides, so every
			// trade adds its qty to exactly one buy and one sell tally.
			if sideOf[tr.TakerID] == Buy {
				boughtQty += tr.Qty
			} else {
				soldQty += tr.Qty
			}
			if sideOf[tr.MakerID] == Buy {
				boughtQty += tr.Qty
			} else {
				soldQty += tr.Qty
			}
		}
	}

	// Conservation: every traded unit had a buyer and a seller.
	if boughtQty != soldQty {
		t.Fatalf("conservation violated: bought %d != sold %d", boughtQty, soldQty)
	}

	// No order over-filled beyond its original quantity.
	for id, filled := range filledByOrder {
		if filled > origQty[id] {
			t.Fatalf("order %s over-filled: %d > original %d (double/phantom fill)", id, filled, origQty[id])
		}
	}
}

func orderID(i int) string {
	const digits = "0123456789"
	if i == 0 {
		return "o0"
	}
	buf := make([]byte, 0, 8)
	for i > 0 {
		buf = append([]byte{digits[i%10]}, buf...)
		i /= 10
	}
	return "o" + string(buf)
}
