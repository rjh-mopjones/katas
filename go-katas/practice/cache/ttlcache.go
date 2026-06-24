// Package cache is the practice skeleton for the caches kata. Implement the SUT
// types from scratch against your own tests; the worked answer key (with tests and
// interview-grade doc comments) lives in the solution/ twin at solution/cache/.
//
// Two exercises to implement here:
//
//	ttlcache.go — Cache[K, V]: a concurrent, generic, TTL'd cache (this file).
//	lru.go      — LRUCache[K, V]: an O(1) LRU over a hand-rolled linked list.
//
// LFU (Exercise 03) is reference-only: there is no skeleton — read solution/cache/lfu.go.
package cache

import (
	"time"

	// Pre-wires golang.org/x/sync so the package compiles before you implement
	// GetOrCompute. Replace this blank import with a real one —
	// "golang.org/x/sync/singleflight" — when you wire up the stampede guard.
	_ "golang.org/x/sync/singleflight"
)

// Option configures a Cache at construction time (functional-options pattern).
type Option func(*options)

type options struct {
	clock         func() time.Time
	sweepInterval time.Duration
}

// WithClock injects the time source (default time.Now) so TTL tests are deterministic.
func WithClock(now func() time.Time) Option { panic("TODO: implement WithClock") }

// WithSweepInterval overrides the sweeper cadence (default = defaultTTL).
func WithSweepInterval(d time.Duration) Option { panic("TODO: implement WithSweepInterval") }

// Cache is a concurrency-safe, generic, TTL'd in-memory cache. Construct with
// NewCache; Close it to stop the background sweeper.
type Cache[K comparable, V any] struct {
	// Design the fields: an RWMutex, a map of entries (value + expiry), the default
	// TTL and injected clock, a done channel + sync.Once for the sweeper, and a
	// singleflight.Group for GetOrCompute.
}

// NewCache builds the cache and starts the background sweeper. defaultTTL <= 0 means
// entries never expire.
func NewCache[K comparable, V any](defaultTTL time.Duration, opts ...Option) *Cache[K, V] {
	panic("TODO: implement NewCache")
}

// Get returns the value and whether it was present and unexpired. Read path: RLock only.
func (c *Cache[K, V]) Get(key K) (V, bool) { panic("TODO: implement Get") }

// Set stores value under key with the default TTL. Write path: full Lock.
func (c *Cache[K, V]) Set(key K, value V) { panic("TODO: implement Set") }

// Delete removes key, returning whether it was present. Write path.
func (c *Cache[K, V]) Delete(key K) bool { panic("TODO: implement Delete") }

// Invalidate is an alias for Delete.
func (c *Cache[K, V]) Invalidate(key K) bool { panic("TODO: implement Invalidate") }

// Clear removes every entry. Write path.
func (c *Cache[K, V]) Clear() { panic("TODO: implement Clear") }

// GetOrCompute returns the cached value, or computes+stores on a miss. Concurrent
// misses on the same key must run fn exactly once (no stampede); errors are not cached.
func (c *Cache[K, V]) GetOrCompute(key K, fn func() (V, error)) (V, error) {
	panic("TODO: implement GetOrCompute")
}

// Len returns the number of stored entries.
func (c *Cache[K, V]) Len() int { panic("TODO: implement Len") }

// Close stops the sweeper. Must be idempotent and leak no goroutine.
func (c *Cache[K, V]) Close() error { panic("TODO: implement Close") }
