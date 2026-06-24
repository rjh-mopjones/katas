package cache

// ---------------------------------------------------------------------------
// Exercise 02: O(1) LRU cache. Implement the doubly-linked list BY HAND (no
// container/list) — the interviewer wants the pointer surgery. Use sentinel
// head/tail nodes so every real node has non-nil prev/next.
// ---------------------------------------------------------------------------

// node is one entry in the intrusive doubly-linked recency list. It stores its key
// so eviction can delete it from the map in O(1). (Provided scaffolding.)
type node[K comparable, V any] struct {
	key        K
	value      V
	prev, next *node[K, V]
}

// LRUCache is a fixed-capacity O(1) LRU cache. Construct with NewLRU. Not safe for
// concurrent use (the concurrent variant must lock Get with a full Mutex — in an LRU
// a read mutates recency).
type LRUCache[K comparable, V any] struct {
	// Design the fields: capacity int, items map[K]*node[K, V], and head/tail
	// sentinel *node[K, V] pointers wired into an empty list in NewLRU.
}

// NewLRU returns an empty LRU with the given capacity (>= 1). Wire up the sentinels here.
func NewLRU[K comparable, V any](capacity int) *LRUCache[K, V] { panic("TODO: implement NewLRU") }

// Get returns the value and, on a hit, promotes key to most-recently-used. A read
// mutates recency.
func (c *LRUCache[K, V]) Get(key K) (V, bool) { panic("TODO: implement Get") }

// Put inserts/updates and marks MRU, evicting the LRU entry when over capacity.
func (c *LRUCache[K, V]) Put(key K, value V) { panic("TODO: implement Put") }

// Len returns the number of entries currently held.
func (c *LRUCache[K, V]) Len() int { panic("TODO: implement Len") }

// addToFront splices n in just after the head sentinel (MRU position).
func (c *LRUCache[K, V]) addToFront(n *node[K, V]) { panic("TODO: implement addToFront") }

// unlink removes n by joining its neighbours.
func (c *LRUCache[K, V]) unlink(n *node[K, V]) { panic("TODO: implement unlink") }

// moveToFront makes n the MRU node (unlink + addToFront).
func (c *LRUCache[K, V]) moveToFront(n *node[K, V]) { panic("TODO: implement moveToFront") }

// removeTail unlinks and returns the LRU node (just before the tail sentinel), or
// nil if empty.
func (c *LRUCache[K, V]) removeTail() *node[K, V] { panic("TODO: implement removeTail") }
