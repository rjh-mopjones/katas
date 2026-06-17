package aggregator

import (
	"context"
	"sync"
)

// subscription is one caller's registration for a market.
//
// ch is buffered (cap = subBuffer) and owned by the registry: only the registry
// closes it. done is closed at the same moment, by the same sync.Once, to release
// the watcher goroutine. Coupling both closes into a single Once is what makes
// "one owner closes each channel, exactly once" structural rather than something
// we have to reason about: every removal path (unsubscribe, ctx-cancel, Close)
// funnels through remove/closeAll, and the Once guarantees the two closes happen
// once total no matter how many paths race.
//
// closed is set under the registry lock before the channels are closed; publish
// checks it under the same lock, so a send never races a close.
type subscription struct {
	id     uint64
	market string
	ch     chan MarketView
	done   chan struct{}
	closed bool
	once   sync.Once
}

// release closes ch and done exactly once. The caller must already have set
// closed=true and removed s from the registry (both under the registry lock).
func (s *subscription) release() {
	s.once.Do(func() {
		close(s.ch)
		close(s.done)
	})
}

// subRegistry holds all subscriptions, keyed by market then id. It has its own
// mutex (independent of the markets map lock) so subscribe/unsubscribe never
// contend with the read/write hot path. The lock is held during publish only to
// serialise against close — sends are non-blocking, so holding it is cheap.
type subRegistry struct {
	mu     sync.Mutex
	nextID uint64
	byMkt  map[string]map[uint64]*subscription
}

func (r *subRegistry) init() {
	r.byMkt = make(map[string]map[uint64]*subscription)
}

// Subscribe registers a buffered channel for market and returns it together with
// an idempotent unsubscribe func. The subscription ends — channel closed, state
// removed — when ANY of these happens: the caller calls unsubscribe, ctx is
// cancelled, or the aggregator is closed (Close closes every channel). A
// goroutine watches ctx and shutdown so cancellation cleans up without the caller
// doing anything; it exits as soon as the subscription is removed by any path,
// because remove closes the subscription's done channel.
func (a *Aggregator) Subscribe(ctx context.Context, market string) (<-chan MarketView, func()) {
	s := a.subs.add(market, a.subBuffer)

	// If we're already closed, hand back a closed channel and a no-op cancel so
	// the caller's range loop terminates immediately rather than blocking. No
	// watcher is launched, so nothing leaks.
	if a.closed.Load() {
		a.subs.remove(s)
		return s.ch, func() {}
	}

	var once sync.Once
	unsub := func() {
		once.Do(func() { a.subs.remove(s) })
	}

	// Watch for ctx cancellation OR aggregator shutdown. Either removes the sub.
	// The goroutine is bounded: remove() closes s.done, so the watcher always
	// terminates — either it triggers the removal itself (ctx / shutdown cases)
	// or it observes a removal driven by another path (the done case).
	go func() {
		select {
		case <-ctx.Done():
			unsub()
		case <-a.ctx.Done():
			unsub()
		case <-s.done:
		}
	}()

	return s.ch, unsub
}

// add creates and registers a new subscription for market.
func (r *subRegistry) add(market string, buf int) *subscription {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.nextID++
	s := &subscription{
		id:     r.nextID,
		market: market,
		ch:     make(chan MarketView, buf),
		done:   make(chan struct{}),
	}
	m := r.byMkt[market]
	if m == nil {
		m = make(map[uint64]*subscription)
		r.byMkt[market] = m
	}
	m[s.id] = s
	return s
}

// remove unregisters a subscription and closes its channels exactly once. Safe to
// call multiple times and from multiple goroutines (unsubscribe, ctx-cancel and
// Close may all fire) because the close is funnelled through s.release()'s Once.
func (r *subRegistry) remove(s *subscription) {
	r.mu.Lock()
	if m := r.byMkt[s.market]; m != nil {
		delete(m, s.id)
		if len(m) == 0 {
			delete(r.byMkt, s.market)
		}
	}
	s.closed = true
	r.mu.Unlock()

	s.release()
}

// publish fans a view out to every subscriber of market. Sends are NON-BLOCKING
// and apply a latest-wins / coalescing policy: if a subscriber's buffer is full
// we drop the OLDEST buffered view and enqueue the newest, so a lagging consumer
// always advances toward the freshest price instead of replaying a stale
// backlog. This is the right policy for live odds — a consumer that fell behind
// wants the current price, not a queue of prices that have already moved. The
// cost is lost intermediate ticks (a consumer may never observe some prices) and
// no ordering guarantee against a concurrent drain; both are acceptable when the
// only quote that matters is the latest. Crucially, publish never blocks the
// ingestion path: a stuck subscriber cannot stall Apply or other subscribers.
//
// We hold the registry lock only to iterate the subscriber set and check the
// closed flag, which is what makes send-on-closed impossible: close happens after
// closed is set under this same lock, so we never send to a channel that is
// concurrently being closed.
func (r *subRegistry) publish(market string, v MarketView) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, s := range r.byMkt[market] {
		if s.closed {
			continue
		}
		select {
		case s.ch <- v:
		default:
			// Buffer full: coalesce to latest. Drop one old value (if still
			// there) and try once more; if the consumer raced us and drained,
			// the second send simply succeeds.
			select {
			case <-s.ch:
			default:
			}
			select {
			case s.ch <- v:
			default:
			}
		}
	}
}

// closeAll closes every subscriber channel exactly once and clears the registry.
// Called only from Aggregator.Close. After this, publish sees an empty registry
// (and any straggler subscription has closed=true), so no send-on-closed occurs.
func (r *subRegistry) closeAll() {
	r.mu.Lock()
	var subs []*subscription
	for _, m := range r.byMkt {
		for _, s := range m {
			s.closed = true
			subs = append(subs, s)
		}
	}
	r.byMkt = make(map[string]map[uint64]*subscription)
	r.mu.Unlock()

	for _, s := range subs {
		s.release()
	}
}
