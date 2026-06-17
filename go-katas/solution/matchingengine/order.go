// Package matchingengine implements the core of an exchange: a limit order book
// that matches incoming orders against resting liquidity under strict
// price-time priority, plus a single-writer matching engine that serialises
// concurrent order flow through the book.
//
// # The model
//
// An order book has two sides — bids (buyers) and asks (sellers). Each side is a
// set of price levels, and each price level is a FIFO queue of resting orders.
// An incoming order is matched against the OPPOSITE side: a buy matches asks, a
// sell matches bids. Anything that does not match immediately either rests on the
// book (a limit order) or is discarded (a market order — see below).
//
// # Why price-time priority, and how FIFO levels implement it
//
// Price-time priority is the fairness rule every regulated exchange uses, and it
// is what makes matching deterministic and auditable:
//
//   - PRICE first. The best price always trades first. For a buyer sweeping the
//     asks that means the lowest ask; for a seller sweeping the bids, the highest
//     bid. Best price = best execution for the incoming (taker) order.
//   - TIME second. Among orders resting at the SAME price, the one that arrived
//     earliest trades first. This is the incentive to post liquidity: you are
//     rewarded for being early at a price.
//
// A FIFO queue per price level implements "time" for free — append on arrival,
// pop from the front when matching — and keeping the price levels sorted (bids
// high→low, asks low→low) implements "price". Walking the best level, draining
// its queue front-to-back, then moving to the next-best level, is exactly
// price-time priority. There is no scoring, no heuristic: the data structure IS
// the policy, which is why the outcome is reproducible from the order arrival
// sequence alone.
//
// # Why money is integer ticks, never float64
//
// Price is an int64 count of ticks (the exchange's minimum price increment), not
// a float. Binary floating point cannot represent most decimal prices exactly
// (0.1 has no finite binary expansion), so float prices drift: equality checks
// fail, sums of fills do not reconcile to the total, and the "same" price level
// can hash to two different float bit patterns. On a money-moving system that is
// a correctness defect — fills that should net to zero leave a phantom fraction
// of a cent. Integers are exact, compare and add exactly, and make a price level
// a clean map key. Convert to a display price (price * tickSize) only at the
// presentation edge.
//
// # The maker-price rule
//
// When an aggressive (taker) order crosses a resting (maker) order, the trade
// executes at the MAKER's price — the resting order's price, the one that was on
// the book first. A buy limit at 105 that hits a resting ask at 102 trades at
// 102, not 105: the taker gets price improvement, and the maker gets exactly the
// price they advertised. This is the standard continuous-auction rule and it
// matters for conservation accounting — both sides of a trade agree on one price.
//
// # Quantity conservation: the money-safety invariant
//
// The single invariant that makes the engine safe is conservation of quantity:
// every unit of quantity that leaves one order's remaining size arrives at
// exactly one counterparty, and no order ever fills beyond its original quantity.
// Each match decrements BOTH the taker's and the resting maker's remaining qty by
// the same traded amount, and a fully-filled resting order is removed from its
// level. There are no phantom fills (quantity created from nowhere) and no double
// fills (the same resting quantity matched twice). The randomized conservation
// test pins this: total bought == total sold, and no order over-fills.
package matchingengine

import "errors"

// Side is the direction of an order: buying or selling.
type Side int

// The two order sides.
const (
	// Buy orders want to acquire quantity; they match against the ask side and,
	// when resting, sort high→low (the highest bid is the best bid).
	Buy Side = iota
	// Sell orders want to dispose of quantity; they match against the bid side
	// and, when resting, sort low→high (the lowest ask is the best ask).
	Sell
)

// String returns the human-readable name of the side.
func (s Side) String() string {
	switch s {
	case Buy:
		return "Buy"
	case Sell:
		return "Sell"
	default:
		return "Unknown"
	}
}

// OrderType is how an order behaves when it cannot fully match: a Limit order
// rests, a Market order never does.
type OrderType int

// The two order types.
const (
	// Limit orders carry a worst acceptable price; any unfilled remainder rests
	// on the book at that price.
	Limit OrderType = iota
	// Market orders take whatever liquidity exists at any price; any unfilled
	// remainder is discarded (a market order never rests on the book).
	Market
)

// String returns the human-readable name of the order type.
func (t OrderType) String() string {
	switch t {
	case Limit:
		return "Limit"
	case Market:
		return "Market"
	default:
		return "Unknown"
	}
}

// Order is a single instruction to buy or sell Qty units. Price is in integer
// ticks (the exchange minimum increment) — see the package doc for why money is
// never a float64. Price is ignored for Market orders.
type Order struct {
	ID    string
	Side  Side
	Type  OrderType
	Price int64
	Qty   int64
}

// Trade is one execution: TakerID is the incoming (aggressive) order, MakerID is
// the resting order it matched, Price is the maker's price, and Qty is the
// quantity that changed hands.
type Trade struct {
	MakerID string
	TakerID string
	Price   int64
	Qty     int64
}

// ErrUnknownOrder is returned when an operation references an order id that is
// not resting on the book.
var ErrUnknownOrder = errors.New("matchingengine: unknown order")

// ErrEngineClosed is returned by Engine.Submit after the engine has been closed.
var ErrEngineClosed = errors.New("matchingengine: engine is closed")
