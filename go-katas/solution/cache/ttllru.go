package cache

import (
	"sync"
	"time"
)

// ---------------------------------------------------------------------------
// TTLLRU: a self-contained, concurrency-safe cache bounded by BOTH capacity (LRU
// eviction) and time (TTL expiry). Built from scratch — it does NOT wrap the
// LRUCache; it owns its own map, doubly-linked recency list, and mutex.
//
// Two eviction axes, one entry:
//   - capacity: when full, drop the least-recently-used entry (the list tail).
//   - time: an entry past its expiresAt is dead and is removed lazily on Get.
//
// They are orthogonal — capacity keys off list position, TTL keys off the clock —
// and both removals are just an unlink + map delete, so they coexist cleanly.
//
// Locking: a full sync.Mutex, NOT an RWMutex. Get reorders the recency list AND
// lazily deletes expired entries, so every operation mutates shared state — there
// is no read-only path an RWMutex could parallelise, and an RLock would let two
// Gets corrupt the list at once.
//
// Memory: capacity-bounded, so growth is always bounded. An expired entry that is
// never read again lingers (occupying a slot) until capacity pressure evicts it; a
// background sweeper to reclaim it eagerly is the natural extension but is not
// needed for correctness.
// ---------------------------------------------------------------------------

// ttlNode is an entry in TTLLRU's intrusive doubly-linked recency list. Unlike the
// lock-free LRUCache's node, it carries its expiry instant directly. It stores its
// key so eviction can delete it from the map in O(1).
type ttlNode[K comparable, V any] struct {
	key        K
	value      V
	expiresAt  time.Time // zero == never expires
	prev, next *ttlNode[K, V]
}

func (n *ttlNode[K, V]) expired(now time.Time) bool {
	return !n.expiresAt.IsZero() && !now.Before(n.expiresAt)
}

// TTLLRU is concurrency-safe, capacity-bounded, and TTL'd. The zero value is not
// usable; construct one with NewTTLLRU.
type TTLLRU[K comparable, V any] struct {
	mu         sync.Mutex
	capacity   int
	ttl        time.Duration
	now        func() time.Time
	items      map[K]*ttlNode[K, V]
	head, tail *ttlNode[K, V] // sentinels: head.next is MRU, tail.prev is LRU
}

// NewTTLLRU returns a cache holding up to capacity entries, each expiring ttl after
// it is written (ttl <= 0 means never expire). clock is the injectable time source
// for deterministic tests; pass nil in production to use time.Now.
func NewTTLLRU[K comparable, V any](capacity int, ttl time.Duration, clock func() time.Time) *TTLLRU[K, V] {
	if capacity < 1 {
		panic("ttllru: capacity must be >= 1")
	}
	if clock == nil {
		clock = time.Now
	}
	head := &ttlNode[K, V]{}
	tail := &ttlNode[K, V]{}
	head.next = tail
	tail.prev = head
	return &TTLLRU[K, V]{
		capacity: capacity,
		ttl:      ttl,
		now:      clock,
		items:    make(map[K]*ttlNode[K, V], capacity),
		head:     head,
		tail:     tail,
	}
}

// Get returns the value if present and unexpired, promoting it to most-recently-used.
// An expired entry is removed (lazy eviction) and reported as a miss.
func (c *TTLLRU[K, V]) Get(key K) (V, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()

	n, ok := c.items[key]
	if !ok {
		var zero V
		return zero, false
	}
	if n.expired(c.now()) {
		c.unlink(n)
		delete(c.items, n.key)
		var zero V
		return zero, false
	}
	c.moveToFront(n)
	return n.value, true
}

// Put stores value under key with a fresh TTL and marks it most-recently-used,
// evicting the least-recently-used entry first if a new insertion exceeds capacity.
func (c *TTLLRU[K, V]) Put(key K, value V) {
	c.mu.Lock()
	defer c.mu.Unlock()

	var exp time.Time
	if c.ttl > 0 {
		exp = c.now().Add(c.ttl)
	}
	if n, ok := c.items[key]; ok { // update in place + refresh TTL and recency
		n.value = value
		n.expiresAt = exp
		c.moveToFront(n)
		return
	}
	n := &ttlNode[K, V]{key: key, value: value, expiresAt: exp}
	c.items[key] = n
	c.addToFront(n)
	if len(c.items) > c.capacity {
		lru := c.tail.prev
		if lru != c.head {
			c.unlink(lru)
			delete(c.items, lru.key)
		}
	}
}

// Len returns the number of entries currently held (any expired-but-not-yet-evicted
// entries included).
func (c *TTLLRU[K, V]) Len() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return len(c.items)
}

// --- doubly-linked list helpers (all called with c.mu held) ----------------

func (c *TTLLRU[K, V]) addToFront(n *ttlNode[K, V]) {
	n.prev = c.head
	n.next = c.head.next
	c.head.next.prev = n
	c.head.next = n
}

func (c *TTLLRU[K, V]) unlink(n *ttlNode[K, V]) {
	n.prev.next = n.next
	n.next.prev = n.prev
	n.prev = nil
	n.next = nil
}

func (c *TTLLRU[K, V]) moveToFront(n *ttlNode[K, V]) {
	c.unlink(n)
	c.addToFront(n)
}
