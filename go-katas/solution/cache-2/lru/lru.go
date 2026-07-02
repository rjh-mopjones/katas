// Package lru implements a thread-safe, fixed-capacity LRU (Least Recently Used)
// cache that reads through to a file-backed store on a miss. It is fully
// self-contained: it shares no types, interfaces, or loader code with the lfu
// package.
//
// Model: the file (e.g. 50 entries) is the slow source of truth; the cache holds
// a small hot subset (e.g. 10). A request hits the cache, or misses and reads
// through to the backing store, inserts the value, and evicts the least recently
// used entry if the cache is now over capacity.
//
// Both Get and Put are O(1): a map gives O(1) lookup, and an intrusive doubly
// linked list gives O(1) move-to-front and O(1) tail removal. A plain slice
// would make reordering O(n), which is why the linked list earns its keep.
package lru

import (
	"bufio"
	"os"
	"strings"
	"sync"
	"sync/atomic"
)

// node is one element of the intrusive doubly linked list. The list is ordered
// most-recently-used (head) to least-recently-used (tail).
type node struct {
	key   string
	value string
	prev  *node
	next  *node
}

// Cache is a thread-safe LRU cache. The zero value is not usable; call New.
type Cache struct {
	mu       sync.Mutex
	capacity int
	items    map[string]*node

	// path is the file-backed store. On a miss the cache scans this file (real
	// disk I/O) rather than preloading it — so the cache exists precisely to
	// avoid repeating that scan. path is set once at construction and never
	// mutated, so it can be read on the miss path WITHOUT holding mu.
	path      string
	loadCount int64 // atomic: number of file scans triggered by misses (for tests)

	// head and tail are sentinels, so the list is never empty and we never need
	// nil checks when splicing. head.next is the MRU, tail.prev is the LRU.
	head *node
	tail *node
}

// New returns an LRU cache holding at most capacity entries, backed by the
// "key=value" lines file at path. The file is NOT read here — it is scanned
// lazily on each miss (see Get). capacity must be > 0.
func New(capacity int, path string) *Cache {
	if capacity <= 0 {
		panic("lru: capacity must be > 0")
	}
	head := &node{}
	tail := &node{}
	head.next = tail
	tail.prev = head
	return &Cache{
		capacity: capacity,
		items:    make(map[string]*node, capacity),
		path:     path,
		head:     head,
		tail:     tail,
	}
}

// Get returns the value for key. On a hit it marks the entry most-recently-used.
// On a miss it scans the backing file (outside the lock), inserts the result, and
// evicts the LRU entry if the cache is over capacity. ok is false when the key is
// absent both in the cache and in the file. err is non-nil only if the file scan
// itself fails (e.g. the file is missing).
func (c *Cache) Get(key string) (value string, ok bool, err error) {
	c.mu.Lock()
	if n, hit := c.items[key]; hit {
		c.moveToFront(n)
		v := n.value
		c.mu.Unlock()
		return v, true, nil
	}
	c.mu.Unlock()

	// Miss: scan the file WITHOUT holding the lock. This is real disk I/O;
	// holding the mutex across it would serialize every caller behind one slow
	// scan. The cost is that two goroutines may scan for the same key
	// concurrently; we reconcile that under the lock below.
	atomic.AddInt64(&c.loadCount, 1)
	loaded, found, err := scanFile(c.path, key)
	if err != nil {
		return "", false, err
	}
	if !found {
		return "", false, nil
	}

	c.mu.Lock()
	defer c.mu.Unlock()
	// Another goroutine may have inserted this key while we were scanning.
	// Treat that winner as authoritative and just refresh recency.
	if n, hit := c.items[key]; hit {
		c.moveToFront(n)
		return n.value, true, nil
	}
	c.insertFront(key, loaded)
	c.evictIfNeeded()
	return loaded, true, nil
}

// Put inserts or updates key=value and marks it most-recently-used, evicting the
// LRU entry if this pushes the cache over capacity.
func (c *Cache) Put(key, value string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if n, hit := c.items[key]; hit {
		n.value = value
		c.moveToFront(n)
		return
	}
	c.insertFront(key, value)
	c.evictIfNeeded()
}

// Len returns the number of entries currently cached.
func (c *Cache) Len() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return len(c.items)
}

// --- list helpers: all callers must hold c.mu ---

func (c *Cache) insertFront(key, value string) {
	n := &node{key: key, value: value}
	c.items[key] = n
	c.link(n, c.head, c.head.next)
}

func (c *Cache) moveToFront(n *node) {
	c.unlink(n)
	c.link(n, c.head, c.head.next)
}

// link splices n between prev and next.
func (c *Cache) link(n, prev, next *node) {
	n.prev = prev
	n.next = next
	prev.next = n
	next.prev = n
}

// unlink removes n from the list.
func (c *Cache) unlink(n *node) {
	n.prev.next = n.next
	n.next.prev = n.prev
	n.prev = nil
	n.next = nil
}

func (c *Cache) evictIfNeeded() {
	if len(c.items) <= c.capacity {
		return
	}
	lru := c.tail.prev // sentinel guarantees this is a real node here
	c.unlink(lru)
	delete(c.items, lru.key)
}

// scanFile scans a "key=value" lines file for key and returns its value. It
// reads line by line and stops as soon as the key is found, so an early key is
// cheaper than a late one — exactly the kind of slow, repeated lookup the cache
// exists to avoid. Blank and malformed lines are skipped.
func scanFile(path, key string) (value string, found bool, err error) {
	f, err := os.Open(path)
	if err != nil {
		return "", false, err
	}
	defer f.Close()

	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" {
			continue
		}
		k, v, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		if strings.TrimSpace(k) == key {
			return strings.TrimSpace(v), true, nil
		}
	}
	if err := sc.Err(); err != nil {
		return "", false, err
	}
	return "", false, nil
}
