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

type LRUCache[K comparable, V any] struct {
	capacity   int
	items      map[K]*node[K, V]
	head, tail *node[K, V]
}
