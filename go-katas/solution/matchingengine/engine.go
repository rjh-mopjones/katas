package matchingengine

import (
	"context"
	"sync"
)

// Engine wraps a single OrderBook behind one matching goroutine fed by a channel.
// Every Submit hands its order to that goroutine and waits for the resulting
// trades, so matching is fully serialised and deterministic.
//
// # Why a single-writer matching loop instead of locking the book
//
// A matching engine is the textbook single-writer state machine. Every operation
// — match, rest, cancel, amend — is a read-modify-write of the SAME shared
// structure (the book), and the ORDER in which they apply is the result: swap two
// concurrent crossing orders and you get different trades. There is essentially
// no read-only or independently-shardable work within one instrument, so a mutex
// around the book would just serialise everything anyway, while adding lock/unlock
// on every access and the ever-present risk of a forgotten critical section
// corrupting real money.
//
// Funnelling all orders through one channel into one goroutine gives the same
// serialisation with a cleaner contract: the book is touched by exactly one
// goroutine and therefore needs no locks at all (lock-free core, concurrency at
// the edge). Determinism falls out for free — the matching loop applies orders in
// the exact order it receives them from the channel, and tests can assert exact
// aggregate outcomes.
//
// # The cost, and how you scale past it
//
// One goroutine means one core: per-instrument throughput is bounded by how fast
// that single loop can match. You do NOT fix this by adding locks to the book;
// you SHARD BY INSTRUMENT. Each symbol gets its own Engine (its own book + loop),
// and a front-end router hashes the order's instrument to the right shard. Orders
// for AAPL and MSFT never touch the same state, so they run on different cores in
// parallel with zero contention, while each instrument keeps its strict, simple
// single-writer ordering. This is exactly how real exchanges partition matching.
//
// The zero value is not usable; construct one with NewEngine and call Start.
type Engine struct {
	book   *OrderBook
	submit chan submitReq

	closeOnce sync.Once
	done      chan struct{} // closed by Close; fences off Submit and stops the loop
	stopped   chan struct{} // closed by the loop when it has exited
}

// submitReq carries one order plus the reply channel the matching loop sends its
// trades back on. The reply channel is buffered (size 1) so the loop never blocks
// delivering the result, even if the caller's context fires first.
type submitReq struct {
	order Order
	reply chan []Trade
}

// NewEngine returns a ready Engine wrapping a fresh OrderBook. Call Start to
// launch the matching loop before submitting.
func NewEngine() *Engine {
	return &Engine{
		book:    NewOrderBook(),
		submit:  make(chan submitReq),
		done:    make(chan struct{}),
		stopped: make(chan struct{}),
	}
}

// Start launches the single matching goroutine. The loop is the ONLY goroutine
// that ever touches the book, which is why the book needs no locks. It ranges on
// the submit channel until Close signals done, then exits and closes stopped so
// Close can confirm a clean stop with no goroutine leak.
func (e *Engine) Start() {
	go func() {
		defer close(e.stopped)
		for {
			select {
			case <-e.done:
				return
			case req := <-e.submit:
				req.reply <- e.book.Submit(req.order)
			}
		}
	}()
}

// Submit hands o to the matching loop and returns the trades it produced, in
// execution order. The call is serialised against every other Submit, so the
// outcome depends only on the order in which Submits reach the loop.
//
// It returns ErrEngineClosed if the engine is (or becomes) closed before the
// order is accepted — never a panic from sending on a closed channel — because
// the send selects against done. If ctx is cancelled before the loop replies,
// Submit returns ctx.Err(); the reply channel is buffered so the loop's send
// still succeeds and nothing leaks.
func (e *Engine) Submit(ctx context.Context, o Order) ([]Trade, error) {
	req := submitReq{order: o, reply: make(chan []Trade, 1)}

	// Hand the order to the loop, but bail cleanly if the engine closes or the
	// caller's context is cancelled. Selecting on done is what makes Submit safe
	// against a concurrent Close (no send on a closed channel).
	select {
	case e.submit <- req:
	case <-e.done:
		return nil, ErrEngineClosed
	case <-ctx.Done():
		return nil, ctx.Err()
	}

	select {
	case trades := <-req.reply:
		return trades, nil
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}

// Close stops the matching loop and blocks until it has exited. It is idempotent
// and safe to call concurrently with in-flight Submits: it closes done (exactly
// once, via closeOnce), which both unblocks any Submit waiting to hand off (they
// get ErrEngineClosed) and tells the loop to return. Close then waits on stopped
// so it only returns once the goroutine is gone — no leak. Submit after Close
// returns ErrEngineClosed.
func (e *Engine) Close() {
	e.closeOnce.Do(func() {
		close(e.done)
	})
	<-e.stopped
}
