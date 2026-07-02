// Package lfu implements a thread-safe, fixed-capacity LFU (Least Frequently
// Used) cache that reads through to a file-backed store on a miss. It is fully
// self-contained: it shares no types, interfaces, or loader code with the lru
// package.
//
// Model: the file (e.g. 50 entries) is the slow source of truth; the cache holds
// a small hot subset (e.g. 10). On a miss the cache reads through to the backing
// store, inserts at frequency 1, and evicts the least frequently used entry if
// over capacity. Ties (multiple entries at the same lowest frequency) are broken
// by recency — the least recently used among them is evicted.
//
// All operations are O(1). The trick is a per-frequency bucket: freqs[f] is a
// doubly linked list of every entry currently at frequency f, ordered MRU→LRU.
// A minFreq cursor tracks the lowest non-empty frequency so eviction is O(1)
// instead of scanning for the minimum.
package lfu

import (
	"bufio"
	"os"
	"strings"
	"sync"
	"sync/atomic"
)

// node lives in exactly one frequency bucket at a time.
type node struct {
	key   string
	value string
	freq  int
	prev  *node
	next  *node
}

// list is a doubly linked list with sentinel head/tail, ordered MRU (head.next)
// to LRU (tail.prev). One list exists per frequency.
type list struct {
	head *node
	tail *node
	size int
}

func newList() *list {
	head := &node{}
	tail := &node{}
	head.next = tail
	tail.prev = head
	return &list{head: head, tail: tail}
}

func (l *list) pushFront(n *node) {
	n.prev = l.head
	n.next = l.head.next
	l.head.next.prev = n
	l.head.next = n
	l.size++
}

func (l *list) remove(n *node) {
	n.prev.next = n.next
	n.next.prev = n.prev
	n.prev = nil
	n.next = nil
	l.size--
}

func (l *list) back() *node { return l.tail.prev } // LRU entry; caller checks size > 0

// Cache is a thread-safe LFU cache. The zero value is not usable; call New.
type Cache struct {
	mu       sync.Mutex
	capacity int
	items    map[string]*node
	freqs    map[int]*list
	minFreq  int

	// path is the file-backed store. On a miss the cache scans this file (real
	// disk I/O) rather than preloading it — so the cache exists precisely to
	// avoid repeating that scan. path is set once at construction and never
	// mutated, so it can be read on the miss path WITHOUT holding mu.
	path      string
	loadCount int64 // atomic: number of file scans triggered by misses (for tests)
}

// New returns an LFU cache holding at most capacity entries, backed by the
// "key=value" lines file at path. The file is NOT read here — it is scanned
// lazily on each miss (see Get). capacity must be > 0.
func New(capacity int, path string) *Cache {
	if capacity <= 0 {
		panic("lfu: capacity must be > 0")
	}
	return &Cache{
		capacity: capacity,
		items:    make(map[string]*node, capacity),
		freqs:    make(map[int]*list),
		path:     path,
	}
}

// Get returns the value for key. On a hit it increments the entry's frequency.
// On a miss it scans the backing file (outside the lock), inserts the result at
// frequency 1, and evicts the least-frequently-used entry if over capacity. ok is
// false when the key is absent both in the cache and in the file. err is non-nil
// only if the file scan itself fails (e.g. the file is missing).
func (c *Cache) Get(key string) (value string, ok bool, err error) {
	c.mu.Lock()
	if n, hit := c.items[key]; hit {
		c.bump(n)
		v := n.value
		c.mu.Unlock()
		return v, true, nil
	}
	c.mu.Unlock()

	// Miss: scan the file WITHOUT holding the lock, so one slow scan doesn't
	// serialize every other caller. Two goroutines may scan for the same key
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
	// Someone may have inserted this key while we were scanning. Honor the winner
	// and just bump its frequency.
	if n, hit := c.items[key]; hit {
		c.bump(n)
		return n.value, true, nil
	}
	c.insert(key, loaded)
	return loaded, true, nil
}

// Put inserts or updates key=value and increments its frequency, evicting the
// least-frequently-used entry if this pushes the cache over capacity.
func (c *Cache) Put(key, value string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if n, hit := c.items[key]; hit {
		n.value = value
		c.bump(n)
		return
	}
	c.insert(key, value)
}

// Len returns the number of entries currently cached.
func (c *Cache) Len() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return len(c.items)
}

// --- helpers: all callers must hold c.mu ---

// insert adds a new entry at frequency 1, evicting first if at capacity. A fresh
// entry always resets minFreq to 1, since 1 is by definition the new minimum.
func (c *Cache) insert(key, value string) {
	if len(c.items) >= c.capacity {
		c.evict()
	}
	n := &node{key: key, value: value, freq: 1}
	c.items[key] = n
	c.bucket(1).pushFront(n)
	c.minFreq = 1
}

// bump moves n from its current frequency bucket to freq+1, advancing minFreq if
// n was the last entry at the old minimum.
func (c *Cache) bump(n *node) {
	old := c.freqs[n.freq]
	old.remove(n)
	if old.size == 0 {
		delete(c.freqs, n.freq)
		if c.minFreq == n.freq {
			c.minFreq++
		}
	}
	n.freq++
	c.bucket(n.freq).pushFront(n)
}

// evict removes the LRU entry within the lowest-frequency bucket.
func (c *Cache) evict() {
	l := c.freqs[c.minFreq]
	victim := l.back()
	l.remove(victim)
	if l.size == 0 {
		delete(c.freqs, c.minFreq)
	}
	delete(c.items, victim.key)
}

// bucket returns the list for frequency f, creating it on first use.
func (c *Cache) bucket(f int) *list {
	l, ok := c.freqs[f]
	if !ok {
		l = newList()
		c.freqs[f] = l
	}
	return l
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
