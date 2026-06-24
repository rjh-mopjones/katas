// Package cache implements three in-memory caches that build on each other, the
// canonical "build a cache from scratch" interview drill:
//
//	ttlcache.go — a concurrency-safe, generic, TTL'd cache with a singleflight
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
	"fmt"
	"sync"
	"time"

	"golang.org/x/sync/singleflight"
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

	// sf collapses concurrent misses on the same key into one fn execution. Its
	// zero value is ready to use.
	sf singleflight.Group

	done      chan struct{}
	closeOnce sync.Once
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
// singleflight.Group.Do elects one caller to run fn and parks the rest on its
// result. fn errors are propagated to every caller and are NOT cached, so a
// transient failure self-heals on the next call.
func (c *Cache[K, V]) GetOrCompute(key K, fn func() (V, error)) (V, error) {
	// Fast path: pure read lock. The overwhelming majority of calls hit here.
	if v, ok := c.Get(key); ok {
		return v, nil
	}
	v, err, _ := c.sf.Do(c.flightKey(key), func() (any, error) {
		// Double-check: another goroutine may have populated key between our
		// fast-path miss and us winning the singleflight slot.
		if v, ok := c.Get(key); ok {
			return v, nil
		}
		computed, err := fn()
		if err != nil {
			return nil, err // do not cache failures
		}
		// Store via Set, which takes the FULL write Lock — not an RLock. singleflight
		// only serialises callers of the *same* key; goroutines computing *distinct*
		// keys store concurrently, and RWMutex permits many RLock holders at once, so
		// an RLock store would be a write/write data race on the map (Go aborts with a
		// fatal "concurrent map writes"). The read-modify-write's write half must be
		// mutually exclusive.
		c.Set(key, computed)
		return computed, nil
	})
	if err != nil {
		var zero V
		return zero, err
	}
	return v.(V), nil
}

// flightKey maps a comparable key to the string singleflight wants. fmt.Sprintf
// "%v" is the pragmatic choice and is collision-free for string/integer keys; the
// alternative that avoids stringification entirely is a map[K]*sync.Mutex of
// per-key locks (more code, no formatting).
func (c *Cache[K, V]) flightKey(key K) string { return fmt.Sprintf("%v", key) }

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
