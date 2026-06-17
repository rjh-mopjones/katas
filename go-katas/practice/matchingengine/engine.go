package matchingengine

import "context"

// Engine wraps a single OrderBook behind one matching goroutine fed by a channel,
// so matching is fully serialised and deterministic and the book needs no locks.
// The zero value is not usable; construct one with NewEngine and call Start.
type Engine struct{}

// NewEngine returns a ready Engine wrapping a fresh OrderBook. Call Start to
// launch the matching loop before submitting.
func NewEngine() *Engine {
	panic("TODO: implement")
}

// Start launches the single matching goroutine — the only goroutine that touches
// the book.
func (e *Engine) Start() {
	panic("TODO: implement")
}

// Submit hands o to the matching loop and returns the trades it produced, in
// execution order. Returns ErrEngineClosed if the engine is closed (never a
// panic), or ctx.Err() if the context is cancelled first.
func (e *Engine) Submit(ctx context.Context, o Order) ([]Trade, error) {
	panic("TODO: implement")
}

// Close stops the matching loop and blocks until it has exited. It is idempotent,
// safe to call concurrently with in-flight Submits, and leaks no goroutine.
func (e *Engine) Close() {
	panic("TODO: implement")
}
