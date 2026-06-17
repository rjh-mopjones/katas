// Package pricecache implements a low-latency, concurrency-safe cache of the
// latest sports-betting prices per market.
//
// The shape of the problem is the canonical Go data-race exercise: a single
// market-data feed goroutine continuously writes the latest price for each
// market while many HTTP handler goroutines read the current price. The naive
// implementation — a bare map[string]Price shared across goroutines — is a data
// race under Go's memory model. Two things go wrong:
//
//   - Concurrent map access is undefined behaviour. The Go runtime actively
//     detects a concurrent map read with a map write and aborts the process with
//     a fatal "concurrent map read and map write" (it is a fatal error, not a
//     recoverable panic). This is not theoretical: the writer reshapes the map's
//     internal buckets while a reader walks them.
//   - Even without the map, the multi-word Price value can tear. A read that
//     overlaps a write may observe Bid from the new price and Ask/Seq from the
//     old one, because the struct copy is not atomic. The Seq field exists so a
//     reader can detect (and a test can assert) that the three fields move as one
//     consistent unit.
//
// PriceCache fixes both by guarding the map with a sync.RWMutex.
//
// # Why RWMutex rather than a plain Mutex
//
// The feed is overwhelmingly read-heavy: one writer, many readers. An RWMutex
// lets all readers proceed concurrently (RLock) and only serialises on the
// comparatively rare write (Lock). A plain sync.Mutex would force readers to
// queue behind each other even though concurrent reads are safe, throttling the
// hot path. The trade-off: RWMutex has higher per-operation overhead and, under
// extreme write contention, can starve or be slower than a Mutex — so for a
// write-heavy or very-short-critical-section workload a plain Mutex (or sharding)
// can win. For a price feed (read:write skewed heavily toward reads) RWMutex is
// the right default.
//
// # Lock-free alternative (the extension)
//
// Because writes replace whole entries, the read path can be made lock-free with
// copy-on-write over an atomic.Pointer[map[string]Price]: a writer clones the map,
// inserts, and atomically swaps the pointer; readers do a single atomic load and
// never block. That trades cheap, wait-free reads for expensive O(n) writes and
// extra allocation, so it suits read-mostly data that changes infrequently.
// sync.Map is another option (optimised for disjoint-key or append-mostly access)
// but its interface{} boxing and lack of a cheap consistent snapshot make it a
// poor fit here. The benchmark vs RWMutex is left as the kata extension.
package pricecache

import "sync"

// Price is an immutable snapshot of a market's two-sided quote.
//
// Seq is a monotonically increasing update sequence number assigned by the feed;
// it lets readers reason about freshness and lets tests assert that Bid, Ask and
// Seq are always observed as one consistent unit (no torn reads).
type Price struct {
	Bid, Ask float64
	Seq      uint64
}

// PriceCache is a concurrency-safe map from market identifier to its latest Price.
//
// It is safe for one writer and many concurrent readers. The zero value is not
// usable; construct one with NewPriceCache.
type PriceCache struct {
	mu     sync.RWMutex
	prices map[string]Price
}

// NewPriceCache returns an empty, ready-to-use PriceCache.
func NewPriceCache() *PriceCache {
	return &PriceCache{prices: make(map[string]Price)}
}

// Set stores the latest price for market, overwriting any previous value.
//
// It takes the write lock because it mutates the shared map; this is the only
// path that excludes readers.
func (c *PriceCache) Set(market string, p Price) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.prices[market] = p
}

// Get returns the latest price for market and whether it was present.
//
// It takes the read lock, so any number of Get calls run concurrently and only
// block while a Set holds the write lock. On a miss it returns the zero Price and
// false.
func (c *PriceCache) Get(market string) (Price, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	p, ok := c.prices[market]
	return p, ok
}

// Snapshot returns a copy of every market's current price.
//
// The returned map is a fresh allocation that the caller owns: it must not alias
// the internal map. Returning the internal map directly would be a correctness
// trap — the caller could iterate or mutate it without holding the lock,
// reintroducing exactly the data race this type exists to prevent. The copy is
// taken under the read lock so it is internally consistent.
func (c *PriceCache) Snapshot() map[string]Price {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make(map[string]Price, len(c.prices))
	for market, p := range c.prices {
		out[market] = p
	}
	return out
}
