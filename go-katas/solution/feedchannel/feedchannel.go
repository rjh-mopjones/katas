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
//     a broadcast: every Publish blocked in select wakes and can observe that done
//     is readable, returning ErrClosed.
//   - updates is closed exactly once, also under closeOnce, AFTER done is
//     signalled. "Only the sender closes, and only once": Close is the sole owner
//     of closing updates, so the subscriber's `range` terminates cleanly.
//
// Closing done is necessary but NOT sufficient to prevent a send-on-closed panic.
// A select with several ready cases chooses uniformly at random, so a Publish
// parked in its send select does not deterministically wake on the done arm: if a
// buffer slot (or a ready receiver) makes the `updates <- u` case ready at the
// same instant Close runs, the runtime may pick the send exactly as close(updates)
// executes — and panic. Closing done first does not order the publisher's choice.
//
// The mutex is what actually makes it safe. Publish holds mu.RLock across its
// check-of-done-and-send; Close takes mu.Lock before closing updates. The write
// lock can only be granted once no Publish holds the read lock — i.e. no Publish
// is mid-send — so closing updates cannot race a send. mu is the linearisation
// point (the same idiom the shutdown kata uses for its jobs channel).
type Broker struct {
	updates   chan Update
	done      chan struct{}
	mu        sync.RWMutex
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
// It never panics on a closed broker. The read lock is held across the check of
// done and the send: while Publish holds it, Close cannot acquire the write lock
// and therefore cannot close the updates channel, so the send is safe even though
// the select might otherwise pick the send arm concurrently with the close. The
// select races three events: the broker closing (done is readable → ErrClosed),
// the context being cancelled or timing out while the buffer is full (→ ctx.Err(),
// so backpressure surfaces as a caller-controlled error rather than data loss or a
// hang), and the send succeeding (→ nil). The fast-path check returns early if the
// broker is already closed; the second select re-checks done so a Publish blocked
// on a full buffer unblocks (with ErrClosed) the moment Close signals.
func (b *Broker) Publish(ctx context.Context, u Update) error {
	b.mu.RLock()
	defer b.mu.RUnlock()

	// Fast path: if already closed, report it without entering the send select.
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
// once. Closing done first wakes every blocked Publish (they return ErrClosed).
// Taking the write lock before closing updates guarantees no Publish is mid-send
// — the write lock cannot be granted while any Publish holds the read lock — so
// closing updates cannot race a send and panic. Closing updates terminates the
// subscriber's range.
func (b *Broker) Close() {
	b.closeOnce.Do(func() {
		close(b.done)

		b.mu.Lock()
		close(b.updates)
		b.mu.Unlock()
	})
}
