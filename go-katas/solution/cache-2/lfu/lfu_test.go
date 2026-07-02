package lfu

import (
	"fmt"
	"sync"
	"sync/atomic"
	"testing"
)

const dataFile = "../data/entries.txt"

// newCache builds a cache backed by the real entries.txt. The file is scanned
// lazily on each miss, so every Get below is an integration test against the
// actual backing store.
func newCache(capacity int) *Cache {
	return New(capacity, dataFile)
}

func TestGetMissThenHit(t *testing.T) {
	c := newCache(10)

	v, ok, err := c.Get("key1")
	if err != nil {
		t.Fatalf("Get(key1): unexpected error %v", err)
	}
	if !ok || v != "value1" {
		t.Fatalf("first Get = (%q, %v), want (value1, true)", v, ok)
	}
	if got := atomic.LoadInt64(&c.loadCount); got != 1 {
		t.Fatalf("file scans after miss = %d, want 1", got)
	}

	v, ok, err = c.Get("key1")
	if err != nil {
		t.Fatalf("Get(key1) #2: unexpected error %v", err)
	}
	if !ok || v != "value1" {
		t.Fatalf("second Get = (%q, %v), want (value1, true)", v, ok)
	}
	if got := atomic.LoadInt64(&c.loadCount); got != 1 {
		t.Fatalf("file scans after hit = %d, want 1 (no rescan)", got)
	}
}

func TestMissNotInStore(t *testing.T) {
	c := newCache(10)
	v, ok, err := c.Get("key999")
	if err != nil {
		t.Fatalf("Get(key999): unexpected error %v", err)
	}
	if ok || v != "" {
		t.Fatalf("Get(key999) = (%q, %v), want (\"\", false)", v, ok)
	}
	if c.Len() != 0 {
		t.Fatalf("Len after missing key = %d, want 0", c.Len())
	}
}

func TestGetBadFile(t *testing.T) {
	c := New(10, "../data/does-not-exist.txt")
	if _, _, err := c.Get("key1"); err == nil {
		t.Fatal("Get against a missing file should return an error")
	}
}

func TestCapacityNeverExceeded(t *testing.T) {
	c := newCache(10)
	for i := 0; i < 50; i++ {
		c.Put(fmt.Sprintf("k%d", i), fmt.Sprintf("v%d", i))
		if c.Len() > 10 {
			t.Fatalf("Len = %d after %d puts, want <= 10", c.Len(), i+1)
		}
	}
	if c.Len() != 10 {
		t.Fatalf("final Len = %d, want 10", c.Len())
	}
}

// TestEvictsLeastFrequentlyUsed: the entry accessed fewest times is evicted.
func TestEvictsLeastFrequentlyUsed(t *testing.T) {
	c := newCache(3)
	c.Put("a", "1") // freq 1
	c.Put("b", "2") // freq 1
	c.Put("c", "3") // freq 1

	// Raise frequencies: a -> 3, b -> 2, c stays at 1 (the lowest).
	c.Get("a")
	c.Get("a")
	c.Get("b")

	c.Put("d", "4") // must evict "c" (lowest frequency)

	if _, ok, _ := c.Get("c"); ok {
		t.Error("c should have been evicted as least frequently used")
	}
	for _, k := range []string{"a", "b", "d"} {
		if _, ok, _ := c.Get(k); !ok {
			t.Errorf("%s should still be cached", k)
		}
	}
}

// TestTieBrokenByRecency: when several entries share the lowest frequency, the
// least recently used among them is evicted.
func TestTieBrokenByRecency(t *testing.T) {
	c := newCache(3)
	c.Put("x", "1")
	c.Put("y", "2")
	c.Put("z", "3") // x, y, z all at freq 1; insertion order x(LRU)..z(MRU)

	c.Put("w", "4") // tie at freq 1 -> evict the LRU of that bucket = "x"

	if _, ok, _ := c.Get("x"); ok {
		t.Error("x should be evicted: oldest among the freq-1 tie")
	}
	if _, ok, _ := c.Get("y"); !ok {
		t.Error("y should remain")
	}
}

// TestMinFreqAdvancesThenResets exercises the minFreq cursor: after entries are
// bumped above 1, a fresh insert resets minFreq to 1 and that fresh entry is the
// next eviction victim.
func TestMinFreqAdvancesThenResets(t *testing.T) {
	c := newCache(2)
	c.Put("a", "1")
	c.Put("b", "2")
	c.Get("a") // a -> 2
	c.Get("b") // b -> 2, minFreq now 2

	c.Put("c", "3") // fresh entry at freq 1; evicts LRU of minFreq(2) = "a"
	if _, ok, _ := c.Get("a"); ok {
		t.Error("a should have been evicted when c was inserted")
	}

	c.Put("d", "4") // minFreq is 1 (c); evict "c"
	if _, ok, _ := c.Get("c"); ok {
		t.Error("c (freq 1) should be evicted before higher-frequency entries")
	}
}

func TestPutUpdatesValue(t *testing.T) {
	c := newCache(2)
	c.Put("a", "1")
	c.Put("a", "updated")
	if v, ok, _ := c.Get("a"); !ok || v != "updated" {
		t.Errorf("Get(a) = (%q, %v), want (updated, true)", v, ok)
	}
}

// TestReadThroughFromFile exercises the full file-backed path.
func TestReadThroughFromFile(t *testing.T) {
	c := newCache(10)
	want := map[string]string{"key7": "value7", "key23": "value23", "key50": "value50"}
	for k, exp := range want {
		v, ok, err := c.Get(k)
		if err != nil {
			t.Fatalf("Get(%s): unexpected error %v", k, err)
		}
		if !ok || v != exp {
			t.Errorf("Get(%s) = (%q, %v), want (%s, true)", k, v, ok, exp)
		}
	}
	if got := atomic.LoadInt64(&c.loadCount); got != int64(len(want)) {
		t.Errorf("file scans = %d, want %d", got, len(want))
	}
}

func TestConcurrentAccess(t *testing.T) {
	c := newCache(10)
	const goroutines, ops = 32, 300

	var wg sync.WaitGroup
	for g := 0; g < goroutines; g++ {
		wg.Add(1)
		go func(g int) {
			defer wg.Done()
			for i := 0; i < ops; i++ {
				key := fmt.Sprintf("key%d", (g+i)%50+1) // key1..key50, all in the file
				if i%2 == 0 {
					if _, _, err := c.Get(key); err != nil {
						t.Errorf("Get(%s): %v", key, err)
						return
					}
				} else {
					c.Put(key, "v")
				}
				if c.Len() > 10 {
					t.Errorf("capacity exceeded: Len = %d", c.Len())
					return
				}
			}
		}(g)
	}
	wg.Wait()

	if c.Len() != 10 {
		t.Fatalf("final Len = %d, want 10", c.Len())
	}
}
