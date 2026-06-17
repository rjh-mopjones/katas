package messagebus

import (
	"sync"
	"time"
)

// pollInterval is how often the dispatcher re-checks ack-timeouts when one is
// pending. It bounds detection latency to a small real duration while keeping the
// check driven by the injected clock (so fake-clock tests are deterministic).
const pollInterval = time.Millisecond

// pending is a message waiting in (or in flight from) a queue, with the
// bookkeeping the queue needs to enforce redelivery, prefetch, and dead-lettering.
type pending struct {
	msg      Message
	attempts int // number of deliveries so far (0 until first dispatch)

	// inflight is true while a delivery is outstanding (delivered, not yet
	// acked/nacked). deadline is the ack-timeout instant for the current inflight
	// delivery, valid only while inflight is true.
	inflight bool
	deadline time.Time

	// gen guards a single inflight delivery against stale acks: each dispatch bumps
	// gen, and an Ack/Nack only takes effect if its captured generation still
	// matches. This makes a redelivered message's old Delivery harmless.
	gen uint64
}

// queue is a bounded FIFO with prefetch (QoS), redelivery, ack-timeout, and
// dead-lettering. It is the per-subscription mailbox: Broker.Publish enqueues into
// it, and a single dispatcher goroutine drains it onto the consumer's delivery
// channel, respecting the prefetch cap.
//
// Concurrency model: all mutable state (the FIFO, the inflight set, the
// dead-letter slice) lives behind mu. The dispatcher goroutine and the consumer's
// Ack/Nack callbacks all mutate that state under mu, then signal the dispatcher to
// re-evaluate via the wake channel (a capacity-1 channel used as an edge-triggered
// "state changed" nudge). This keeps the locking simple — no condition variable,
// no lock held across a channel send to the consumer.
type queue struct {
	out        chan Delivery
	maxRetries int
	prefetch   int
	ackTimeout time.Duration
	now        func() time.Time

	mu          sync.Mutex
	fifo        []*pending            // ready-to-dispatch messages, head = fifo[0]
	inflight    int                   // count of unacked deliveries outstanding
	inflightSet map[*pending]struct{} // set of currently-inflight pendings (for ack-timeout scans)
	deadLetter  []Message
	closed      bool

	wake chan struct{} // capacity-1 nudge to the dispatcher
	done chan struct{} // closed by Close to stop the dispatcher
	wg   sync.WaitGroup
}

// newQueue constructs and starts a queue's dispatcher goroutine.
func newQueue(size, prefetch, maxRetries int, ackTimeout time.Duration, now func() time.Time) *queue {
	q := &queue{
		out:         make(chan Delivery, size),
		maxRetries:  maxRetries,
		prefetch:    prefetch,
		ackTimeout:  ackTimeout,
		now:         now,
		inflightSet: make(map[*pending]struct{}),
		wake:        make(chan struct{}, 1),
		done:        make(chan struct{}),
	}
	q.wg.Add(1)
	go q.dispatch()
	return q
}

// publish appends m to the FIFO, returning ErrQueueFull if the queue (ready +
// inflight) is at capacity, or ErrClosed if it has been closed.
//
// Capacity counts both ready and inflight messages so an at-capacity consumer
// that is merely slow to ack cannot be flooded. The policy is non-blocking
// (shed-on-full) rather than blocking: a blocking publish couples the publisher's
// latency to the slowest consumer and risks deadlocking Publish under a held lock.
// Shedding with ErrQueueFull pushes the load decision to the caller, mirroring a
// RabbitMQ queue with a max-length overflow=reject-publish policy.
func (q *queue) publish(m Message) error {
	q.mu.Lock()
	if q.closed {
		q.mu.Unlock()
		return ErrClosed
	}
	if len(q.fifo)+q.inflight >= cap(q.out) {
		q.mu.Unlock()
		return ErrQueueFull
	}
	q.fifo = append(q.fifo, &pending{msg: m})
	q.mu.Unlock()
	q.nudge()
	return nil
}

// nudge wakes the dispatcher to re-evaluate. The capacity-1, non-blocking send
// coalesces bursts: if a wake is already pending the extra nudge is dropped, since
// one re-evaluation observes all accumulated state.
func (q *queue) nudge() {
	select {
	case q.wake <- struct{}{}:
	default:
	}
}

// dispatch is the queue's single owning goroutine. It is the only place that
// sends on and closes q.out, which is how the broker honours "only the owner
// closes, exactly once" and avoids any send-on-closed panic.
//
// Each iteration: expire any ack-timeouts (turning them into redeliveries),
// compute the next eligible message under the prefetch cap, and either dispatch it
// or block until something changes (a publish, an ack/nack, the next ack-timeout,
// or Close). The ack-timeout wait uses a timer derived from the injected clock's
// view so tests can drive it with a fake clock and no real sleeps.
func (q *queue) dispatch() {
	defer q.wg.Done()
	defer close(q.out)

	for {
		q.mu.Lock()
		if q.closed {
			q.mu.Unlock()
			return
		}
		q.expireTimeouts()

		d, ok, wait := q.next()
		q.mu.Unlock()

		if ok {
			select {
			case q.out <- d:
			case <-q.done:
				return
			}
			continue
		}

		// Nothing dispatchable now: wait for a state change, the next ack-timeout,
		// or shutdown. A nil timer channel disables the timeout arm.
		//
		// When an ack-timeout is pending (wait > 0) we cannot sleep for `wait`
		// directly, because deadlines are measured against the INJECTED clock — a
		// test advances a fake clock instantly, so a real timer of `wait` would
		// never fire in step. Instead we poll on a small fixed real interval and
		// re-evaluate expireTimeouts against the injected clock each tick. This
		// keeps ack-timeout detection deterministic under a fake clock with no
		// dependence on real elapsed time, while a wake/done still unblocks
		// instantly.
		var timer <-chan time.Time
		var t *time.Timer
		if wait > 0 {
			t = time.NewTimer(pollInterval)
			timer = t.C
		}
		select {
		case <-q.wake:
		case <-timer:
		case <-q.done:
			if t != nil {
				t.Stop()
			}
			return
		}
		if t != nil {
			t.Stop()
		}
	}
}

// next picks the head message to dispatch, if any, under the prefetch cap. It
// returns the Delivery to send, whether one is available, and — when none is — how
// long to wait before the soonest pending ack-timeout (0 means "wait for an
// external nudge only"). Caller must hold mu.
func (q *queue) next() (Delivery, bool, time.Duration) {
	if q.inflight >= q.prefetch {
		// At the prefetch ceiling: dispatch nothing; only an ack/nack (which
		// decrements inflight and nudges) or a timeout can change that. Report the
		// soonest timeout so the dispatcher can wake to expire it.
		return Delivery{}, false, q.soonestTimeout()
	}
	if len(q.fifo) == 0 {
		return Delivery{}, false, q.soonestTimeout()
	}

	p := q.fifo[0]
	q.fifo = q.fifo[1:]
	p.attempts++
	p.inflight = true
	p.gen++
	q.inflight++
	q.inflightSet[p] = struct{}{}
	if q.ackTimeout > 0 {
		p.deadline = q.now().Add(q.ackTimeout)
	}

	gen := p.gen
	d := Delivery{
		Message:     p.msg,
		Redelivered: p.attempts,
		queue:       q,
	}
	d.ackOnce = q.makeAck(p, gen)
	return d, true, 0
}

// makeAck builds the once-effective Ack/Nack callback for a specific dispatch
// (identified by p and its generation gen). Capturing gen makes the callback
// idempotent and stale-safe: only the dispatch that produced this callback can act
// on p, so a late Ack on a delivery that already timed out and was redelivered is
// silently ignored.
func (q *queue) makeAck(p *pending, gen uint64) func(ack bool, requeue bool) {
	var once sync.Once
	return func(ack bool, requeue bool) {
		once.Do(func() {
			q.mu.Lock()
			defer q.mu.Unlock()
			if p.gen != gen || !p.inflight {
				return // stale: this delivery was already superseded.
			}
			q.settle(p, ack, requeue)
		})
	}
}

// settle resolves an inflight delivery: ack drops it; nack-no-requeue dead-letters
// it; nack-with-requeue redelivers it unless the retry cap is exceeded, in which
// case it is dead-lettered. Caller must hold mu.
func (q *queue) settle(p *pending, ack bool, requeue bool) {
	p.inflight = false
	q.inflight--
	delete(q.inflightSet, p)

	switch {
	case ack:
		// Done. The message simply leaves the system.
	case !requeue:
		q.deadLetter = append(q.deadLetter, p.msg)
	case p.attempts > q.maxRetries:
		// Redeliveries exhausted: poison message → dead-letter queue.
		q.deadLetter = append(q.deadLetter, p.msg)
	default:
		q.requeueFront(p)
	}
	q.nudge()
}

// expireTimeouts redelivers (or dead-letters) any inflight message whose
// ack-timeout has passed. Caller must hold mu. A zero ackTimeout disables this.
func (q *queue) expireTimeouts() {
	if q.ackTimeout <= 0 {
		return
	}
	now := q.now()
	// Inflight messages have left the FIFO, so we scan the inflightSet for any whose
	// ack-deadline has passed.
	for p := range q.inflightSet {
		if p.inflight && !p.deadline.IsZero() && !now.Before(p.deadline) {
			// Treat an ack-timeout as a nack-with-requeue: redeliver, or dead-letter
			// once the retry cap is hit. Bump gen so the original Delivery's late ack
			// becomes a no-op.
			p.gen++
			q.settle(p, false, true)
		}
	}
}

// soonestTimeout returns the duration until the nearest inflight ack-timeout, or 0
// if none. Caller must hold mu.
func (q *queue) soonestTimeout() time.Duration {
	if q.ackTimeout <= 0 {
		return 0
	}
	// Any inflight deadline at all means the dispatcher must keep polling, so we
	// return a positive duration (>= pollInterval) whenever one exists. A return of
	// 0 means strictly "no deadline pending" — never "a deadline is due now" — which
	// avoids a sentinel collision that would let an already-overdue timeout disable
	// the poll and wedge the dispatcher.
	hasDeadline := false
	for p := range q.inflightSet {
		if p.inflight && !p.deadline.IsZero() {
			hasDeadline = true
			break
		}
	}
	if !hasDeadline {
		return 0
	}
	return pollInterval
}

// requeueFront returns a redelivered message to the head of the FIFO, preserving
// approximate ordering (RabbitMQ requeues to the front too). Caller must hold mu.
func (q *queue) requeueFront(p *pending) {
	p.deadline = time.Time{}
	q.fifo = append([]*pending{p}, q.fifo...)
}

// deadLettered returns a snapshot copy of the dead-letter queue.
func (q *queue) deadLettered() []Message {
	q.mu.Lock()
	defer q.mu.Unlock()
	out := make([]Message, len(q.deadLetter))
	copy(out, q.deadLetter)
	return out
}

// close stops the dispatcher and (once it exits) closes q.out exactly once. It is
// safe to call once; the broker guards multi-close with sync.Once at its level.
func (q *queue) close() {
	q.mu.Lock()
	if q.closed {
		q.mu.Unlock()
		return
	}
	q.closed = true
	q.mu.Unlock()
	close(q.done)
	q.wg.Wait()
}
