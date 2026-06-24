package cache

// ---------------------------------------------------------------------------
// Exercise 02: O(1) LRU (least-recently-used) cache.
//
// Get and Put are both O(1) via two structures working together: a map for O(1)
// lookup, and a doubly-linked list for O(1) recency ordering (most-recently-used
// at the front, least-recently-used at the back). The list is hand-rolled rather
// than container/list so the pointer surgery is explicit.
//
// The single biggest correctness aid is the pair of SENTINEL nodes (a dummy head
// and tail): every real node always has non-nil prev/next, so addToFront/unlink
// never special-case the empty list or the first/last node.
// ---------------------------------------------------------------------------

// node is one entry in the intrusive doubly-linked recency list. It stores its own
// key so that when removeTail hands back the evicted node, the caller can delete it
// from the map in O(1) — without the key there is no way back from node to map key.
type node[K comparable, V any] struct {
	key        K
	value      V
	prev, next *node[K, V]
}

// LRUCache is a fixed-capacity O(1) LRU cache.
//
// It is NOT safe for concurrent use. Because Get mutates recency (see below), a
// concurrency-safe wrapper must guard Get with a full Mutex, not an RWMutex —
// "a read is a write" in an LRU. The zero value is not usable; use NewLRU.
type LRUCache[K comparable, V any] struct {
	capacity   int
	items      map[K]*node[K, V]
	head, tail *node[K, V] // sentinels: head.next is MRU, tail.prev is LRU
}

// NewLRU returns an empty LRU with the given capacity (which must be >= 1). It wires
// the two sentinels into an empty list (head <-> tail) so the list is never empty of
// nodes.
func NewLRU[K comparable, V any](capacity int) *LRUCache[K, V] {
	if capacity < 1 {
		panic("lru: capacity must be >= 1")
	}
	head := &node[K, V]{}
	tail := &node[K, V]{}
	head.next = tail
	tail.prev = head
	return &LRUCache[K, V]{
		capacity: capacity,
		items:    make(map[K]*node[K, V], capacity),
		head:     head,
		tail:     tail,
	}
}

// Get returns the value for key and whether it was present, and on a hit promotes
// key to most-recently-used.
//
// Note that Get MUTATES state: it reorders the recency list. In an LRU a read is a
// write, which is exactly why the concurrent variant cannot serve Get under a read
// lock.
func (c *LRUCache[K, V]) Get(key K) (V, bool) {
	n, ok := c.items[key]
	if !ok {
		var zero V
		return zero, false
	}
	c.moveToFront(n)
	return n.value, true
}

// Put inserts or updates key=value and marks it most-recently-used, evicting the
// least-recently-used entry first if a new insertion would exceed capacity.
func (c *LRUCache[K, V]) Put(key K, value V) {
	if n, ok := c.items[key]; ok {
		n.value = value  // update in place — must not grow the map
		c.moveToFront(n) // updating also refreshes recency
		return
	}
	n := &node[K, V]{key: key, value: value}
	c.items[key] = n
	c.addToFront(n)
	if len(c.items) > c.capacity {
		evicted := c.removeTail()
		if evicted != nil {
			delete(c.items, evicted.key)
		}
	}
}

// Len returns the number of entries currently held.
func (c *LRUCache[K, V]) Len() int { return len(c.items) }

// addToFront splices n in just after the head sentinel (the MRU position).
func (c *LRUCache[K, V]) addToFront(n *node[K, V]) {
	n.prev = c.head
	n.next = c.head.next
	c.head.next.prev = n
	c.head.next = n
}

// unlink removes n from the list by joining its neighbours to each other. Clearing
// n's pointers afterwards is hygiene against accidental reuse of a stale link.
func (c *LRUCache[K, V]) unlink(n *node[K, V]) {
	n.prev.next = n.next
	n.next.prev = n.prev
	n.prev = nil
	n.next = nil
}

// moveToFront makes n the MRU node. Used by Get and by Put on an existing key.
func (c *LRUCache[K, V]) moveToFront(n *node[K, V]) {
	c.unlink(n)
	c.addToFront(n)
}

// removeTail unlinks and returns the LRU node (the one just before the tail
// sentinel), or nil if the list is empty.
func (c *LRUCache[K, V]) removeTail() *node[K, V] {
	lru := c.tail.prev
	if lru == c.head { // empty list
		return nil
	}
	c.unlink(lru)
	return lru
}
