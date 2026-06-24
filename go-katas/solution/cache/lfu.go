package cache

// ---------------------------------------------------------------------------
// Exercise 03: O(1) LFU (least-frequently-used) cache. REFERENCE ONLY.
//
// There is no practice skeleton for this one — read it, don't drill it. The goal
// is to be able to sketch the frequency-bucket structure on a whiteboard and
// deliver the LRU-vs-LFU trade-off (below) out loud.
//
// # Structure
//
//	items:   map[K]*lfuEntry          O(1) lookup; each entry knows its frequency
//	freqs:   map[int]*freqList        freq -> list of entries at exactly that freq,
//	                                  ordered MRU(front)..LRU(back)
//	minFreq: int                      smallest freq present; the eviction victim is
//	                                  freqs[minFreq].back()
//
// Within one frequency bucket, ties are broken by recency (LFU with an LRU
// tiebreak), reusing the same sentinel-head/tail list idea as the LRU exercise.
//
// # Maintaining minFreq in O(1)
//
//   - Insert: a new entry has frequency 1, the global minimum, so minFreq = 1.
//   - Access (touch): the entry moves from freq f to f+1. If that drained the
//     minFreq bucket, the entry we just promoted was the last one at the minimum,
//     so the new minimum is exactly f+1 — minFreq++. We never search for it.
//   - Eviction: remove freqs[minFreq].back(); no recompute needed, because the very
//     next insert resets minFreq to 1.
//
// Every operation is a map lookup plus a constant number of pointer splices plus an
// integer bump — O(1), no bucket ever scanned.
//
// # Trade-off: LRU vs LFU (the interview one-liner, expanded)
//
//   - LRU evicts by recency: simple, cheap, the sensible default. Its weakness is a
//     one-off sequential scan (a batch touching millions of cold keys once), which
//     walks the whole working set through the cache and flushes the genuinely hot
//     set — recency cannot tell "hot" from "touched once, just now".
//   - LFU evicts by frequency: it resists scans (a once-touched key has frequency 1
//     and dies before your frequency-100 hot keys). Its weakness is the mirror
//     image: a historically-hot-now-cold entry keeps its high count and squats
//     forever, because plain LFU has no aging/decay.
//   - Production "LFU" is almost always approximate: exact per-key counters are
//     costly and go stale without decay, so real systems use a windowed/decaying
//     frequency sketch — e.g. TinyLFU (a Count-Min sketch with periodic halving) as
//     an admission filter in front of an LRU/SLRU eviction list. That is the design
//     in Caffeine (Java) and Ristretto (Go).
//
// Bottom line: LRU is the right default; LFU only earns its place with stable,
// long-lived hotspots AND a decay mechanism — and in practice you reach for an
// approximate, decaying scheme like TinyLFU rather than exact LFU.
// ---------------------------------------------------------------------------

// lfuEntry is one item plus its access frequency and its links within the list for
// that frequency.
type lfuEntry[K comparable, V any] struct {
	key        K
	value      V
	freq       int
	prev, next *lfuEntry[K, V]
}

// freqList is a doubly-linked list with sentinel head/tail; front is MRU, back is
// LRU within a single frequency bucket.
type freqList[K comparable, V any] struct {
	head, tail *lfuEntry[K, V]
	size       int
}

func newFreqList[K comparable, V any]() *freqList[K, V] {
	h, t := &lfuEntry[K, V]{}, &lfuEntry[K, V]{}
	h.next, t.prev = t, h
	return &freqList[K, V]{head: h, tail: t}
}

func (l *freqList[K, V]) pushFront(e *lfuEntry[K, V]) {
	e.prev, e.next = l.head, l.head.next
	l.head.next.prev = e
	l.head.next = e
	l.size++
}

func (l *freqList[K, V]) remove(e *lfuEntry[K, V]) {
	e.prev.next = e.next
	e.next.prev = e.prev
	e.prev, e.next = nil, nil
	l.size--
}

// back returns the LRU entry within this bucket, or nil if the bucket is empty.
func (l *freqList[K, V]) back() *lfuEntry[K, V] {
	if l.size == 0 {
		return nil
	}
	return l.tail.prev
}

// LFUCache is a fixed-capacity O(1) LFU cache (with an LRU tiebreak within a
// frequency). Not safe for concurrent use. The zero value is not usable; use NewLFU.
type LFUCache[K comparable, V any] struct {
	capacity int
	items    map[K]*lfuEntry[K, V]
	freqs    map[int]*freqList[K, V]
	minFreq  int
}

// NewLFU returns an empty LFU cache with the given capacity.
func NewLFU[K comparable, V any](capacity int) *LFUCache[K, V] {
	return &LFUCache[K, V]{
		capacity: capacity,
		items:    make(map[K]*lfuEntry[K, V], capacity),
		freqs:    make(map[int]*freqList[K, V]),
	}
}

// touch bumps e from frequency f to f+1, maintaining the freq buckets and minFreq.
func (c *LFUCache[K, V]) touch(e *lfuEntry[K, V]) {
	f := e.freq
	c.freqs[f].remove(e)
	if f == c.minFreq && c.freqs[f].size == 0 {
		c.minFreq++
	}
	e.freq = f + 1
	bucket, ok := c.freqs[e.freq]
	if !ok {
		bucket = newFreqList[K, V]()
		c.freqs[e.freq] = bucket
	}
	bucket.pushFront(e)
}

// Get returns the value for key and whether it was present, bumping its frequency
// on a hit.
func (c *LFUCache[K, V]) Get(key K) (V, bool) {
	e, ok := c.items[key]
	if !ok {
		var zero V
		return zero, false
	}
	c.touch(e)
	return e.value, true
}

// Put inserts or updates key=value, evicting the least-frequently-used entry (LRU
// among the minimum-frequency bucket) if a new insertion would exceed capacity.
func (c *LFUCache[K, V]) Put(key K, value V) {
	if c.capacity <= 0 {
		return
	}
	if e, ok := c.items[key]; ok {
		e.value = value
		c.touch(e)
		return
	}
	if len(c.items) >= c.capacity {
		victim := c.freqs[c.minFreq].back()
		c.freqs[c.minFreq].remove(victim)
		delete(c.items, victim.key)
	}
	e := &lfuEntry[K, V]{key: key, value: value, freq: 1}
	c.items[key] = e
	bucket, ok := c.freqs[1]
	if !ok {
		bucket = newFreqList[K, V]()
		c.freqs[1] = bucket
	}
	bucket.pushFront(e)
	c.minFreq = 1
}

// Len returns the number of entries currently held.
func (c *LFUCache[K, V]) Len() int { return len(c.items) }
