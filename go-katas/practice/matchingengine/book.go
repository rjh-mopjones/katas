package matchingengine

// OrderBook is a single-instrument limit order book. It is NOT safe for
// concurrent use — serialising access is the Engine's job. The zero value is not
// usable; construct one with NewOrderBook.
type OrderBook struct{}

// NewOrderBook returns an empty, ready-to-use OrderBook.
func NewOrderBook() *OrderBook {
	panic("TODO: implement")
}

// Submit matches o against the opposite side under price-time priority and
// returns the trades that resulted, in execution order. Fills execute at the
// maker (resting) price. A Limit remainder rests on the book; a Market remainder
// is discarded.
func (b *OrderBook) Submit(o Order) []Trade {
	panic("TODO: implement")
}

// Cancel removes a resting order so it can no longer fill. Returns true if the
// order was resting and removed, false if the id is unknown.
func (b *OrderBook) Cancel(orderID string) bool {
	panic("TODO: implement")
}

// Amend changes the remaining quantity of a resting order. Reducing the quantity
// keeps time priority; increasing it (or re-pricing) loses priority and is
// modelled as cancel-and-resubmit. Returns false for an unknown id or an increase.
func (b *OrderBook) Amend(orderID string, newQty int64) bool {
	panic("TODO: implement")
}

// Best returns the best bid and best ask currently resting, and ok=false if
// either side is empty.
func (b *OrderBook) Best() (bestBid int64, bestAsk int64, ok bool) {
	panic("TODO: implement")
}
