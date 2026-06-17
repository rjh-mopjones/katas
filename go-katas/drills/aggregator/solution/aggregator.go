// Package aggregator is a low-latency market-data aggregator for a sports-betting
// trading firm. It ingests a high-rate stream of per-venue back/lay quotes and
// serves, per market, the current best back (highest available to back) and best
// lay (lowest available to lay) across all venues, expiring quotes older than a
// TTL. Callers can also subscribe to a market and be pushed the freshest view as
// it changes.
//
// # Concurrency design (the WHY)
//
// The workload is read-heavy: a handful of venue feeds write, but many request
// handlers read the current price on the hot path. Two layers of state make the
// read path nearly lock-free:
//
//  1. A top-level map[string]*market guarded by an RWMutex. This lock is taken
//     ONLY to find-or-create a market entry. The fast path (lookup of an existing
//     market) takes an RLock, which lets all readers and all per-market writers
//     proceed in parallel. The exclusive Lock is taken only on the rare event of
//     seeing a brand-new market for the first time. The set of live markets is
//     small and effectively bounded, so creation is a cold path.
//
//  2. Each *market publishes its computed best view through an
//     atomic.Pointer[MarketView]. Readers do view.Load() with no lock at all —
//     a single atomic pointer read. Writers compute a fresh immutable view under
//     the market's own Mutex and atomically swap it in (copy-on-write per market).
//
// Rejected alternative: a single big RWMutex (or copy-on-write) over the WHOLE
// markets map. That would serialise every read against every write to any market
// and force readers to hold a lock (RLock still has cache-line contention on the
// lock word, and blocks writers). Per-market copy-on-write localises contention
// to a single market and keeps the global read path to one atomic load. The cost
// of our approach is an allocation per update (the new MarketView) — acceptable
// because updates are far rarer than reads, and the GC pressure is small, fixed-
// size structs. If updates ever dominated, a mutex-protected mutable view would
// trade read-path purity for fewer allocations; for THIS read-heavy workload the
// atomic snapshot wins.
//
// # Clock injection (the WHY)
//
// Staleness is time-dependent. A real clock would force tests to sleep, making
// them slow and flaky. The clock is an injectable func() time.Time so tests can
// advance time deterministically and assert exactly when a quote expires.
//
// # pprof note
//
// If Get latency regressed in production I'd open a CPU profile first
// (go tool pprof of /debug/pprof/profile) to see whether time is going to lock
// contention, atomic stalls, or map hashing on the find-or-create path. If ns/op
// looked fine but allocs/op or GC time climbed, I'd switch to a heap/alloc
// profile (-alloc_objects) to confirm the per-update MarketView allocation is the
// driver and consider pooling. BenchmarkGet below is the local proxy for that
// hot path.
package aggregator

import (
	"context"
	"sync"
	"sync/atomic"
	"time"
)

// PriceUpdate is a single back/lay quote from one venue for one market, stamped
// with the time it was observed.
type PriceUpdate struct {
	Venue  string
	Market string
	Back   float64
	Lay    float64
	Ts     time.Time
}

// MarketView is the immutable published best-of-book for a market: the best back
// and best lay, each tagged with the venue that sourced it and the timestamp of
// that quote. A zero-valued side (price 0, zero Ts) means no fresh quote exists
// for that side. Views are never mutated after publication; a new value is built
// and atomically swapped in on each change, so a reader holding a *MarketView
// sees a consistent snapshot.
type MarketView struct {
	Market string

	Back      float64
	BackVenue string
	BackTs    time.Time

	Lay      float64
	LayVenue string
	LayTs    time.Time
}

// market holds the per-venue quotes for one market and the atomically published
// view. venues is mutable state guarded by mu; view is the lock-free read path.
type market struct {
	name string

	mu     sync.Mutex
	venues map[string]PriceUpdate

	// view is the copy-on-write snapshot. Readers Load() it without any lock.
	view atomic.Pointer[MarketView]
}

// Option configures an Aggregator at construction. Functional options keep the
// constructor backwards-compatible and let callers set only what they care about.
type Option func(*Aggregator)

// WithTTL sets how long a venue quote stays fresh. After ttl elapses (per the
// injected clock) the quote is dropped from the best view.
func WithTTL(ttl time.Duration) Option {
	return func(a *Aggregator) { a.ttl = ttl }
}

// WithClock injects the time source used for staleness. Defaults to time.Now.
// Tests pass a closure over an atomic value to advance time deterministically.
func WithClock(now func() time.Time) Option {
	return func(a *Aggregator) {
		if now != nil {
			a.now = now
		}
	}
}

// WithSubscriberBuffer sets the per-subscriber channel capacity. A larger buffer
// tolerates burstier consumers before the latest-wins coalescing kicks in.
func WithSubscriberBuffer(n int) Option {
	return func(a *Aggregator) {
		if n > 0 {
			a.subBuffer = n
		}
	}
}

// WithSweepInterval sets how often the background sweeper recomputes views to
// expire quotes that went stale with no new updates arriving.
func WithSweepInterval(d time.Duration) Option {
	return func(a *Aggregator) {
		if d > 0 {
			a.sweepEvery = d
		}
	}
}

// Aggregator is the concurrent market-data aggregator. Construct with New.
type Aggregator struct {
	mu      sync.RWMutex // guards markets map shape (find-or-create only)
	markets map[string]*market

	now        func() time.Time
	ttl        time.Duration
	subBuffer  int
	sweepEvery time.Duration

	subs subRegistry

	ctx    context.Context
	cancel context.CancelFunc
	wg     sync.WaitGroup
	closed atomic.Bool
}

// New returns a started Aggregator. It launches a background sweeper goroutine
// that expires stale quotes even when no new updates arrive; Close stops it.
func New(opts ...Option) *Aggregator {
	a := &Aggregator{
		markets:    make(map[string]*market),
		now:        time.Now,
		ttl:        5 * time.Second,
		subBuffer:  16,
		sweepEvery: time.Second,
	}
	for _, opt := range opts {
		opt(a)
	}
	a.subs.init()
	a.ctx, a.cancel = context.WithCancel(context.Background())

	a.wg.Add(1)
	go a.sweepLoop()
	return a
}

// findOrCreate returns the market entry for name, creating it if absent. The
// RLock fast path covers the common case (market already exists); the exclusive
// Lock is taken only to insert a brand-new market, double-checking under the
// write lock to avoid two creators racing.
func (a *Aggregator) findOrCreate(name string) *market {
	a.mu.RLock()
	m := a.markets[name]
	a.mu.RUnlock()
	if m != nil {
		return m
	}

	a.mu.Lock()
	defer a.mu.Unlock()
	if m = a.markets[name]; m != nil {
		return m
	}
	m = &market{name: name, venues: make(map[string]PriceUpdate)}
	a.markets[name] = m
	return m
}

// lookup returns the existing market or nil, taking only the RLock.
func (a *Aggregator) lookup(name string) *market {
	a.mu.RLock()
	m := a.markets[name]
	a.mu.RUnlock()
	return m
}

// Apply ingests one price update. It is safe to call from many goroutines. If
// the aggregator is closed it is a no-op (so a producer racing Close never
// panics). The new best view is computed under the market's own lock and the
// fresh snapshot is published both via the atomic pointer and to subscribers.
func (a *Aggregator) Apply(u PriceUpdate) {
	if a.closed.Load() {
		return
	}
	m := a.findOrCreate(u.Market)

	m.mu.Lock()
	m.venues[u.Venue] = u
	view := m.computeView(a.now(), a.ttl)
	m.mu.Unlock()

	m.view.Store(view)
	a.subs.publish(u.Market, *view)
}

// computeView recomputes the best back/lay over the non-stale venue quotes.
// Best back is the highest price (most generous to a backer); best lay is the
// lowest price (cheapest to lay). Caller holds m.mu. The returned view is a
// fresh immutable value ready to be atomically published.
func (m *market) computeView(now time.Time, ttl time.Duration) *MarketView {
	v := &MarketView{Market: m.name}
	haveBack, haveLay := false, false
	for _, q := range m.venues {
		if now.Sub(q.Ts) > ttl {
			continue // stale
		}
		if q.Back > 0 && (!haveBack || q.Back > v.Back) {
			v.Back, v.BackVenue, v.BackTs, haveBack = q.Back, q.Venue, q.Ts, true
		}
		if q.Lay > 0 && (!haveLay || q.Lay < v.Lay) {
			v.Lay, v.LayVenue, v.LayTs, haveLay = q.Lay, q.Venue, q.Ts, true
		}
	}
	return v
}

// Get returns the current best view for a market and whether any fresh side
// exists. The read path is lock-free past the RLock map lookup: it Load()s the
// atomic snapshot, then drops any side whose timestamp is now older than the TTL
// (so a view that has gone stale since the last write/sweep is never served).
// Returns (zero, false) for an unknown market or one with no fresh side.
func (a *Aggregator) Get(market string) (MarketView, bool) {
	m := a.lookup(market)
	if m == nil {
		return MarketView{}, false
	}
	snap := m.view.Load()
	if snap == nil {
		return MarketView{}, false
	}

	now := a.now()
	out := MarketView{Market: snap.Market}
	fresh := false
	if snap.Back > 0 && now.Sub(snap.BackTs) <= a.ttl {
		out.Back, out.BackVenue, out.BackTs, fresh = snap.Back, snap.BackVenue, snap.BackTs, true
	}
	if snap.Lay > 0 && now.Sub(snap.LayTs) <= a.ttl {
		out.Lay, out.LayVenue, out.LayTs, fresh = snap.Lay, snap.LayVenue, snap.LayTs, true
	}
	if !fresh {
		return MarketView{}, false
	}
	return out, true
}

// sweepLoop periodically recomputes every market's view so quotes that aged past
// the TTL expire even with no new updates arriving (the Stage 3 requirement that
// staleness is time-driven, not update-driven). It exits cleanly when the
// aggregator's context is cancelled by Close — this is the Stage 3<->6 link: the
// sweeper is the one long-lived goroutine and Close must join it.
func (a *Aggregator) sweepLoop() {
	defer a.wg.Done()
	t := time.NewTicker(a.sweepEvery)
	defer t.Stop()
	for {
		select {
		case <-a.ctx.Done():
			return
		case <-t.C:
			a.sweepOnce()
		}
	}
}

// sweepOnce snapshots the market pointers under RLock (so it never holds the
// global lock while doing per-market work), then recomputes and republishes each.
func (a *Aggregator) sweepOnce() {
	a.mu.RLock()
	ms := make([]*market, 0, len(a.markets))
	for _, m := range a.markets {
		ms = append(ms, m)
	}
	a.mu.RUnlock()

	now := a.now()
	for _, m := range ms {
		m.mu.Lock()
		view := m.computeView(now, a.ttl)
		m.mu.Unlock()
		m.view.Store(view)
		a.subs.publish(m.name, *view)
	}
}

// Close stops the aggregator. It is idempotent and safe to call concurrently
// with Apply/Subscribe. It flips the closed flag (so further Apply is a no-op),
// cancels the context (stopping the sweeper), closes every subscriber channel
// exactly once, then waits for the sweeper goroutine to exit.
//
// Drain vs abandon: we ABANDON in-flight publishes rather than draining them. A
// committed view (one already Store()d to the atomic pointer) is never lost —
// Get continues to serve the last good snapshot until Close returns — but we do
// not promise to flush queued subscriber sends. For live odds this is the
// correct trade: a price in a closing subscriber's buffer is already stale by the
// time anyone reads it, and blocking shutdown to drain it would risk a hang if a
// consumer never reads. The ownership rule is "one owner closes each channel,
// exactly once": the registry owns subscriber channels and closes them here;
// publishers only ever send (and only under the registry lock, which guards
// against send-on-closed).
func (a *Aggregator) Close() error {
	if !a.closed.CompareAndSwap(false, true) {
		return nil // already closed
	}
	a.cancel()
	a.subs.closeAll()
	a.wg.Wait()
	return nil
}
