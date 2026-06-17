// Package feedchannel implements a price-feed fan-out broker built on channels.
//
// Publishers push price updates with Publish; a single subscriber consumes them
// by ranging over Updates(). The point of the kata is correct channel discipline
// — the three classic channel bugs that bite every Go engineer, and the idioms
// that eliminate them:
//
//	(a) Send on a closed channel panics.
//	    `close(ch)` is a one-way, irreversible signal, and a subsequent `ch <- v`
//	    panics with "send on closed channel". The fix is the close-once / only the
//	    sender closes rule: shutdown is signalled by closing a separate `done`
//	    channel exactly once (guarded by sync.Once), and Publish selects on
//	    `done` so it observes shutdown and returns ErrClosed instead of racing to
//	    send on a channel that is about to be (or already) closed. We never let a
//	    publisher be the thing that touches a closed `updates` channel.
//
//	(b) A nil channel blocks forever.
//	    Receiving from or sending to a nil channel blocks the goroutine
//	    permanently. A zero-value Broker (`var b Broker`) has nil channels, so its
//	    Publish/Updates would hang with no error — construct via NewBroker. The
//	    same property is a tool, not just a hazard: setting a channel variable to
//	    nil inside a select disables that case, which is how you remove a branch
//	    from a select loop without restructuring it.
//
//	(c) A full (or unbuffered) channel applies backpressure.
//	    A send blocks until a receiver is ready (or buffer space exists). That is
//	    desirable — it stops a fast publisher from unbounded memory growth — but a
//	    naive `ch <- v` blocks indefinitely if the subscriber stalls. The bounded
//	    buffer (NewBroker(buffer)) absorbs bursts, and the send is wrapped in a
//	    select on ctx.Done() so a blocked publisher surfaces backpressure as
//	    ctx.Err() (caller-controlled timeout/cancel) rather than hanging.
//
// # Blocking vs dropping (the policy trade-off)
//
// This broker blocks the publisher under backpressure (bounded by ctx). The
// alternative is a dropping policy: add a `default:` arm to the send select so a
// full buffer drops the update and returns immediately. Dropping favours
// publisher latency and bounded memory at the cost of data loss (acceptable for a
// price feed where only the latest tick matters); blocking favours completeness
// at the cost of latency/coupling to the slowest consumer. Choose per stream.
package feedchannel

import (
	"context"
	"errors"
	"sync"
)

// ErrClosed is returned by Publish when the broker has been closed. Publishing to
// a closed broker is a no-op error, never a panic.
var ErrClosed = errors.New("feedchannel: broker closed")

// Update is an immutable price tick for a single market.
type Update struct {
	Market string
	Price  float64
}

// Broker is a single-subscriber, fan-in price-feed broker.
//
// Many goroutines may call Publish concurrently; one goroutine consumes Updates().
// The zero value is unusable (its channels are nil and would block forever) —
// construct one with NewBroker.
//
// Shutdown is coordinated by two channels and a sync.Once:
//
//   - done is closed exactly once by Close (via closeOnce). Closing a channel is
//     a broadcast: every Publish blocked in select wakes, sees done is readable,
//     and returns ErrClosed. This is why Publish never sends on a closed updates
//     channel — it loses the race to the done signal by construction.
//   - updates is closed exactly once, also under closeOnce, AFTER done is
//     signalled. "Only the sender closes, and only once": Close is the sole owner
//     of closing updates, so the subscriber's `range` terminates cleanly and no
//     publisher can panic by sending afterward (they have already bailed on done).
type Broker struct {
	updates   chan Update
	done      chan struct{}
	closeOnce sync.Once
}

// NewBroker returns a ready Broker whose updates channel has the given buffer
// capacity. The buffer is the bounded backpressure window: publishers proceed
// without blocking while fewer than buffer updates are in flight, and block
// (subject to ctx) once it is full. A buffer of 0 is a valid unbuffered broker —
// every Publish then rendezvouses directly with the subscriber.
func NewBroker(buffer int) *Broker {
	return &Broker{
		updates: make(chan Update, buffer),
		done:    make(chan struct{}),
	}
}

// Publish sends u to the subscriber, blocking under backpressure until space is
// available, the context is done, or the broker is closed.
//
// It never panics on a closed broker. The select races three events: the broker
// closing (done is readable → ErrClosed), the context being cancelled or timing
// out while the buffer is full (→ ctx.Err(), so backpressure surfaces as a
// caller-controlled error rather than data loss or a hang), and the send
// succeeding (→ nil). Selecting on done before/with the send is what guarantees
// we never execute `updates <- u` on a channel Close has closed.
func (b *Broker) Publish(ctx context.Context, u Update) error {
	// Fast path: if already closed, report it without risking the send case being
	// chosen in a race with a ready buffer slot.
	select {
	case <-b.done:
		return ErrClosed
	default:
	}

	select {
	case <-b.done:
		return ErrClosed
	case <-ctx.Done():
		return ctx.Err()
	case b.updates <- u:
		return nil
	}
}

// Updates returns the receive-only side of the feed for the subscriber to range
// over. The range loop ends when Close closes the channel.
func (b *Broker) Updates() <-chan Update {
	return b.updates
}

// Close shuts the broker down. It is idempotent and safe to call concurrently
// with Publish: the sync.Once ensures done and updates are each closed exactly
// once. Closing done first wakes every blocked Publish (they return ErrClosed)
// before updates is closed, upholding the "only the sender closes, only once"
// rule so no Publish can ever send on the closed updates channel. Closing updates
// terminates the subscriber's range.
func (b *Broker) Close() {
	b.closeOnce.Do(func() {
		close(b.done)
		close(b.updates)
	})
}
