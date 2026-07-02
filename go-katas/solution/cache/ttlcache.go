// Package cache implements three in-memory caches that build on each other, the
// canonical "build a cache from scratch" interview drill:
//
//	ttlcache.go — a concurrency-safe, generic, TTL'd cache with a stampede-proof
//	              GetOrCompute (Exercise 01).
//	lru.go      — an O(1) LRU cache backed by a hand-rolled doubly-linked list
//	              and a map (Exercise 02).
//	lfu.go      — an O(1) LFU cache with frequency buckets; reference-only,
//	              read it, don't drill it (Exercise 03).
//
// # Why one package, three types
//
// They share a theme — bounded/expiring in-memory state under concurrent access —
// but solve different problems: TTL bounds by *time*, LRU bounds by *capacity via
// recency*, LFU bounds by *capacity via frequency*. Keeping them together makes the
// trade-offs easy to compare side by side.
//
// # Concurrency model (TTL cache)
//
// A cache is overwhelmingly read-heavy, so the TTL cache guards its map with a
// sync.RWMutex: unlimited readers run concurrently under RLock, and only the
// comparatively rare writes serialise on the full Lock. A plain sync.Mutex would
// force readers to queue behind each other for no reason; the trade-off is that
// RWMutex has higher per-op overhead and, under heavy *write* contention, a plain
// Mutex or a sharded map can win. For read-mostly cache traffic RWMutex is the
// right default.
package cache

import (
	"sync"
	"time"
)

// Option configures a Cache at construction time (the functional-options pattern).
// Construction stays a one-argument call — NewCache(ttl) — while tests can opt into
// a fake clock or a custom sweep cadence without widening the constructor for every
// caller.
type Option func(*options)

type options struct {
	clock         func() time.Time
	sweepInterval time.Duration
}

// WithClock injects the time source. Tests pass a fake, advanceable clock so TTL
// expiry is exercised deterministically instead of with flaky time.Sleep; in
// production the option is omitted and the cache uses time.Now.
func WithClock(now func() time.Time) Option {
	return func(o *options) { o.clock = now }
}

// WithSweepInterval overrides how often the background sweeper wakes to evict
// expired entries. It defaults to the cache's defaultTTL.
func WithSweepInterval(d time.Duration) Option {
	return func(o *options) { o.sweepInterval = d }
}

// ttlEntry is a stored value plus the instant it expires. A zero expiresAt means
// "never expires" (used when defaultTTL <= 0).
type ttlEntry[V any] struct {
	value     V
	expiresAt time.Time
}

// expired reports whether the entry is dead as of now. The boundary instant counts
// as expired: !now.Before(x) is exactly now >= x, matching the usual "TTL elapsed"
// intuition.
func (e ttlEntry[V]) expired(now time.Time) bool {
	return !e.expiresAt.IsZero() && !now.Before(e.expiresAt)
}

// Cache is a concurrency-safe, generic, TTL'd in-memory cache.
//
// The zero value is not usable; construct one with NewCache. Always Close it when
// finished so the background sweeper goroutine stops — otherwise it runs until the
// process exits (a goroutine leak).
type Cache[K comparable, V any] struct {
	mu         sync.RWMutex
	items      map[K]ttlEntry[V]
	defaultTTL time.Duration
	now        func() time.Time

	// inflight tracks keys currently being computed by GetOrCompute, so concurrent
	// callers for the same key join one computation instead of each running fn (a
	// hand-rolled singleflight). Guarded by mu.
	inflight map[K]*call[V]

	done      chan struct{}
	closeOnce sync.Once
}

// call is one in-flight GetOrCompute computation. The leader runs fn and writes
// val/err, then calls wg.Done(); waiters block on wg.Wait() and then read val/err
// (the WaitGroup provides the happens-before, so the read is race-free).
type call[V any] struct {
	wg  sync.WaitGroup
	val V
	err error
}

// NewCache returns a ready-to-use cache whose entries expire defaultTTL after they
// are written, and starts the background sweeper goroutine. A defaultTTL <= 0 means
// entries never expire.
func NewCache[K comparable, V any](defaultTTL time.Duration, opts ...Option) *Cache[K, V] {
	o := options{clock: time.Now, sweepInterval: defaultTTL}
	for _, opt := range opts {
		opt(&o)
	}
	c := &Cache[K, V]{
		items:      make(map[K]ttlEntry[V]),
		inflight:   make(map[K]*call[V]),
		defaultTTL: defaultTTL,
		now:        o.clock,
		done:       make(chan struct{}),
	}
	if o.sweepInterval > 0 {
		go c.sweepLoop(o.sweepInterval)
	}
	return c
}

// Get returns the value for key and whether it was present and unexpired.
//
// This is the read path: it takes only the RLock, so any number of Gets run
// concurrently and block only while a writer holds the full Lock. On a miss
// (absent or expired) it returns the zero V and false. It deliberately does NOT
// delete an expired entry it finds — doing so would force the read path to upgrade
// to a write lock. Reclaiming the memory is the sweeper's job; serving a stale
// value is what we must never do, and the expired check here guarantees that.
func (c *Cache[K, V]) Get(key K) (V, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	e, ok := c.items[key]
	if !ok || e.expired(c.now()) {
		var zero V
		return zero, false
	}
	return e.value, true
}

// Set stores value under key with the cache's default TTL, overwriting any existing
// entry. It takes the full write Lock because it mutates the shared map.
func (c *Cache[K, V]) Set(key K, value V) {
	c.mu.Lock()
	defer c.mu.Unlock()
	var exp time.Time
	if c.defaultTTL > 0 {
		exp = c.now().Add(c.defaultTTL)
	}
	c.items[key] = ttlEntry[V]{value: value, expiresAt: exp}
}

// Delete removes key, returning whether it was present. Write path.
func (c *Cache[K, V]) Delete(key K) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	if _, ok := c.items[key]; !ok {
		return false
	}
	delete(c.items, key)
	return true
}

// Invalidate is a readability alias for Delete, for eviction call sites that read
// more naturally as "invalidate this key".
func (c *Cache[K, V]) Invalidate(key K) bool { return c.Delete(key) }

// Clear removes every entry. Reallocating the map rather than deleting key-by-key
// lets the old backing array be collected in one go. Write path.
func (c *Cache[K, V]) Clear() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.items = make(map[K]ttlEntry[V])
}

// GetOrCompute returns the cached value for key, or — on a miss — computes it with
// fn, stores it, and returns it. If N goroutines miss the same key at once, fn runs
// exactly once for that key and the other N-1 share the result.
//
// This is the cache-stampede (a.k.a. thundering-herd / dogpile) guard: without it,
// a cold miss on a hot key sends N identical expensive calls (DB query, upstream
// RPC) at the very moment that downstream is already cold and struggling.
//
// The implementation is a hand-rolled singleflight (no external dependency): an
// in-flight map records which keys are currently being computed. The first caller
// for a key becomes the leader, runs fn (NOT holding any lock — fn may be slow
// I/O), and stores the result; concurrent callers find the in-flight entry, block
// on its WaitGroup, and share the leader's result. fn errors are propagated to the
// waiters and are NOT cached, so a transient failure self-heals on the next call.
func (c *Cache[K, V]) GetOrCompute(key K, fn func() (V, error)) (V, error) {
	// Fast path: pure read lock. The overwhelming majority of calls hit here.
	if v, ok := c.Get(key); ok {
		return v, nil
	}

	c.mu.Lock()
	// Double-check under the lock — another goroutine may have populated key between
	// our fast-path miss and acquiring the lock.
	if e, ok := c.items[key]; ok && !e.expired(c.now()) {
		c.mu.Unlock()
		return e.value, nil
	}
	// If someone is already computing this key, join them: block on their call.
	if cl, ok := c.inflight[key]; ok {
		c.mu.Unlock()
		cl.wg.Wait()
		return cl.val, cl.err
	}
	// Otherwise we are the leader. Register the in-flight call before releasing mu.
	cl := &call[V]{}
	cl.wg.Add(1)
	c.inflight[key] = cl
	c.mu.Unlock()

	// Run fn without holding the lock. Set takes the full write Lock itself.
	cl.val, cl.err = fn()
	if cl.err == nil {
		c.Set(key, cl.val) // do not cache failures
	}

	// Deregister and wake every waiter. Writing val/err before wg.Done() establishes
	// the happens-before that lets waiters read them race-free.
	c.mu.Lock()
	delete(c.inflight, key)
	c.mu.Unlock()
	cl.wg.Done()

	return cl.val, cl.err
}

// Len returns the number of entries currently stored, including any expired ones
// the sweeper has not yet reclaimed. Intended for tests and metrics.
func (c *Cache[K, V]) Len() int {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return len(c.items)
}

// Close stops the background sweeper. It is idempotent: sync.Once guards the
// close(done) so a second Close cannot panic on an already-closed channel. The
// io.Closer-shaped error return is always nil here.
func (c *Cache[K, V]) Close() error {
	c.closeOnce.Do(func() { close(c.done) })
	return nil
}

// sweepLoop is the background eviction goroutine. It wakes on a wall-clock ticker
// and — crucially — selects on done so it returns the instant Close is called. That
// done arm is the whole difference between a clean shutdown and a leaked goroutine.
func (c *Cache[K, V]) sweepLoop(interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-c.done:
			return
		case <-ticker.C:
			c.sweep()
		}
	}
}

// sweep evicts everything expired as of now, under the write lock. Deleting during
// a range over a map is explicitly allowed in Go.
func (c *Cache[K, V]) sweep() {
	now := c.now()
	c.mu.Lock()
	defer c.mu.Unlock()
	for k, e := range c.items {
		if e.expired(now) {
			delete(c.items, k)
		}
	}
}
