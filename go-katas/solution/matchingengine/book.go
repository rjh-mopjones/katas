package matchingengine

import "sort"

// restingOrder is an order living on the book with a mutable remaining quantity.
// We track remaining (not the original Qty) because partial fills decrement it in
// place; the order is removed once remaining hits zero.
type restingOrder struct {
	id        string
	price     int64
	remaining int64
}

// priceLevel is the FIFO queue of resting orders at one price. Insertion order
// IS time priority: new orders append to the back, matching pops from the front,
// so the earliest arrival at a price always trades first.
type priceLevel struct {
	price  int64
	orders []*restingOrder
}

// OrderBook is a single-instrument limit order book. It is NOT safe for
// concurrent use — it is a plain state machine with no internal locking. That is
// deliberate: serialising access is the Engine's job (see engine.go), which keeps
// this core a clean single-writer structure that is easy to reason about and to
// test deterministically.
//
// Each side is kept as a slice of price levels held in matching-priority order:
//   - bids: highest price first (index 0 is the best bid),
//   - asks: lowest price first (index 0 is the best ask).
//
// A side index (orderID -> side, price) lets Cancel and Amend find a resting
// order in O(1) without scanning every level.
//
// The zero value is not usable; construct one with NewOrderBook.
type OrderBook struct {
	bids []*priceLevel
	asks []*priceLevel

	// index locates a resting order's side and price level for Cancel/Amend.
	index map[string]orderLoc
}

// orderLoc records where a resting order lives so Cancel/Amend can find it.
type orderLoc struct {
	side  Side
	price int64
}

// NewOrderBook returns an empty, ready-to-use OrderBook.
func NewOrderBook() *OrderBook {
	return &OrderBook{index: make(map[string]orderLoc)}
}

// Submit matches o against the opposite side under price-time priority and
// returns the trades that resulted, in execution order.
//
// Matching walks the best opposite level first, draining its FIFO queue
// front-to-back (time priority), then advances to the next-best level (price
// priority), as long as o still has quantity AND the level is crossable:
//
//   - a buy crosses an ask level when o.Price >= askPrice (a Market buy crosses
//     any ask);
//   - a sell crosses a bid level when o.Price <= bidPrice (a Market sell crosses
//     any bid).
//
// Each fill executes at the MAKER's (resting) price and decrements BOTH orders'
// remaining quantity by the same amount — the conservation invariant. A resting
// order drained to zero is removed from its level and the side index.
//
// After matching, any remainder of o is handled by type: a Limit order rests on
// its own side at o.Price (joining the back of that price level — last in time);
// a Market order's remainder is discarded (market orders never rest).
func (b *OrderBook) Submit(o Order) []Trade {
	remaining := o.Qty
	var trades []Trade

	// Choose the book we match against and a crossable predicate for o's price
	// versus a resting level price.
	var opposite *[]*priceLevel
	var crosses func(restPrice int64) bool
	if o.Side == Buy {
		opposite = &b.asks
		crosses = func(restPrice int64) bool {
			return o.Type == Market || o.Price >= restPrice
		}
	} else {
		opposite = &b.bids
		crosses = func(restPrice int64) bool {
			return o.Type == Market || o.Price <= restPrice
		}
	}

	// Sweep the opposite book best-first. *opposite is kept in priority order, so
	// index 0 is always the current best level.
	for remaining > 0 && len(*opposite) > 0 {
		level := (*opposite)[0]
		if !crosses(level.price) {
			break // best level no longer crosses; nothing deeper can either.
		}

		// Drain this level FIFO: front of the queue is the earliest arrival.
		for remaining > 0 && len(level.orders) > 0 {
			maker := level.orders[0]
			fill := remaining
			if maker.remaining < fill {
				fill = maker.remaining
			}

			trades = append(trades, Trade{
				MakerID: maker.id,
				TakerID: o.ID,
				Price:   maker.price, // maker-price rule
				Qty:     fill,
			})

			// Conservation: the same fill leaves both sides.
			maker.remaining -= fill
			remaining -= fill

			if maker.remaining == 0 {
				delete(b.index, maker.id)
				level.orders = level.orders[1:] // pop the front
			}
		}

		if len(level.orders) == 0 {
			*opposite = (*opposite)[1:] // level exhausted; drop it.
		}
	}

	// Rest the remainder of a limit order; a market order's remainder evaporates.
	if remaining > 0 && o.Type == Limit {
		b.rest(o, remaining)
	}

	return trades
}

// rest places remaining units of o onto its own side at o.Price, appending to
// the back of that price level (last in time) and recording the side index.
func (b *OrderBook) rest(o Order, remaining int64) {
	ro := &restingOrder{id: o.ID, price: o.Price, remaining: remaining}

	var side *[]*priceLevel
	if o.Side == Buy {
		side = &b.bids
	} else {
		side = &b.asks
	}

	for _, lvl := range *side {
		if lvl.price == o.Price {
			lvl.orders = append(lvl.orders, ro)
			b.index[o.ID] = orderLoc{side: o.Side, price: o.Price}
			return
		}
	}

	// New price level: append then re-sort into priority order.
	*side = append(*side, &priceLevel{price: o.Price, orders: []*restingOrder{ro}})
	b.sortSide(o.Side)
	b.index[o.ID] = orderLoc{side: o.Side, price: o.Price}
}

// sortSide re-establishes priority order for a side: bids high→low, asks low→high.
func (b *OrderBook) sortSide(side Side) {
	if side == Buy {
		sort.Slice(b.bids, func(i, j int) bool { return b.bids[i].price > b.bids[j].price })
	} else {
		sort.Slice(b.asks, func(i, j int) bool { return b.asks[i].price < b.asks[j].price })
	}
}

// Cancel removes a resting order from the book so it can no longer fill. It
// returns true if the order was resting and removed, false if the id is unknown
// (e.g. already fully filled or never rested).
func (b *OrderBook) Cancel(orderID string) bool {
	loc, ok := b.index[orderID]
	if !ok {
		return false
	}
	b.removeFromLevel(orderID, loc)
	delete(b.index, orderID)
	return true
}

// Amend changes the remaining quantity of a resting order.
//
// Reducing the quantity (newQty < current remaining) is done IN PLACE and keeps
// the order's position in its FIFO level — its time priority is preserved,
// because shrinking your order does not advantage you over orders that were
// behind you. This is the case implemented here.
//
// Two operations are deliberately NOT a simple in-place edit and would lose time
// priority in a real exchange: INCREASING the quantity (you are asking for more
// fill than orders that queued after you, so you go to the back of the level),
// and changing the PRICE (you move to a different level entirely, last in time).
// Production amend-up / re-price is therefore modelled as cancel-and-resubmit. We
// reject a non-reducing newQty here rather than silently keeping priority, so the
// priority semantics stay honest.
//
// A newQty <= 0 is treated as a full cancel. Returns false for an unknown id or a
// quantity that would increase the order.
func (b *OrderBook) Amend(orderID string, newQty int64) bool {
	loc, ok := b.index[orderID]
	if !ok {
		return false
	}
	if newQty <= 0 {
		b.removeFromLevel(orderID, loc)
		delete(b.index, orderID)
		return true
	}

	ro := b.find(orderID, loc)
	if ro == nil {
		return false
	}
	if newQty > ro.remaining {
		return false // amend-up loses priority; modelled as cancel+resubmit.
	}
	ro.remaining = newQty
	return true
}

// Best returns the best bid and best ask currently resting, and ok=false if
// either side is empty (there is no two-sided market to quote).
func (b *OrderBook) Best() (bestBid int64, bestAsk int64, ok bool) {
	if len(b.bids) == 0 || len(b.asks) == 0 {
		return 0, 0, false
	}
	return b.bids[0].price, b.asks[0].price, true
}

// find returns the resting order at loc, or nil if absent.
func (b *OrderBook) find(orderID string, loc orderLoc) *restingOrder {
	for _, lvl := range b.levelsFor(loc.side) {
		if lvl.price != loc.price {
			continue
		}
		for _, ro := range lvl.orders {
			if ro.id == orderID {
				return ro
			}
		}
	}
	return nil
}

// removeFromLevel deletes orderID from its price level, dropping the level if it
// becomes empty. Caller is responsible for the index entry.
func (b *OrderBook) removeFromLevel(orderID string, loc orderLoc) {
	var side *[]*priceLevel
	if loc.side == Buy {
		side = &b.bids
	} else {
		side = &b.asks
	}
	for li, lvl := range *side {
		if lvl.price != loc.price {
			continue
		}
		for oi, ro := range lvl.orders {
			if ro.id == orderID {
				lvl.orders = append(lvl.orders[:oi], lvl.orders[oi+1:]...)
				if len(lvl.orders) == 0 {
					*side = append((*side)[:li], (*side)[li+1:]...)
				}
				return
			}
		}
	}
}

// levelsFor returns the price-level slice for a side.
func (b *OrderBook) levelsFor(side Side) []*priceLevel {
	if side == Buy {
		return b.bids
	}
	return b.asks
}
