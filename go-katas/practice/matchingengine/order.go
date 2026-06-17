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
	panic("TODO: implement")
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
	panic("TODO: implement")
}

// Order is a single instruction to buy or sell Qty units. Price is in integer
// ticks (the exchange minimum increment) — money is never a float64. Price is
// ignored for Market orders.
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
