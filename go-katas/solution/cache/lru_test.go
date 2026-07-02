package cache

import (
	"sync"
	"testing"
)

func TestPutThenGet(t *testing.T) {
	c := NewLRU[string, int](2)
	c.Put("a", 1)
	if got, ok := c.Get("a"); !ok || got != 1 {
		t.Fatalf("Get(a) = (%d, %v), want (1, true)", got, ok)
	}
}

func TestLRUGetMissing(t *testing.T) {
	c := NewLRU[string, int](2)
	if v, ok := c.Get("nope"); ok || v != 0 {
		t.Fatalf("Get(nope) = (%d, %v), want (0, false)", v, ok)
	}
}

// TestUpdateExistingKeyDoesNotGrow asserts updating a present key overwrites in
// place rather than adding a second entry.
func TestUpdateExistingKeyDoesNotGrow(t *testing.T) {
	c := NewLRU[string, int](2)
	c.Put("a", 1)
	c.Put("a", 2)
	if c.Len() != 1 {
		t.Fatalf("Len() = %d after updating existing key, want 1", c.Len())
	}
	if v, _ := c.Get("a"); v != 2 {
		t.Fatalf("Get(a) = %d after update, want 2", v)
	}
}

// TestEvictsLeastRecentlyUsed fills past capacity and asserts the LRU key is gone
// while more-recently-used keys survive.
func TestEvictsLeastRecentlyUsed(t *testing.T) {
	c := NewLRU[string, int](2)
	c.Put("a", 1) // LRU
	c.Put("b", 2)
	c.Put("c", 3) // size would be 3 > cap 2 -> evict a

	if c.Len() != 2 {
		t.Fatalf("Len() = %d, want 2 (capacity)", c.Len())
	}
	if _, ok := c.Get("a"); ok {
		t.Fatalf("a still present; expected it evicted as least-recently-used")
	}
	if v, ok := c.Get("b"); !ok || v != 2 {
		t.Fatalf("b = (%d, %v), want (2, true)", v, ok)
	}
	if v, ok := c.Get("c"); !ok || v != 3 {
		t.Fatalf("c = (%d, %v), want (3, true)", v, ok)
	}
}

// TestGetPromotesSurvivesEviction is the heart of LRU: a Get on the oldest key
// promotes it, so the next insert evicts something else.
func TestGetPromotesSurvivesEviction(t *testing.T) {
	c := NewLRU[string, int](2)
	c.Put("a", 1)
	c.Put("b", 2)

	if _, ok := c.Get("a"); !ok { // touch a -> b becomes LRU
		t.Fatalf("precondition: Get(a) ok = false, want true")
	}

	c.Put("c", 3) // should evict b, not a

	if _, ok := c.Get("b"); ok {
		t.Fatalf("b still present; expected it evicted (a was promoted by Get)")
	}
	if v, ok := c.Get("a"); !ok || v != 1 {
		t.Fatalf("a = (%d, %v), want (1, true) — Get should have saved it", v, ok)
	}
	if v, ok := c.Get("c"); !ok || v != 3 {
		t.Fatalf("c = (%d, %v), want (3, true)", v, ok)
	}
}

// TestPutExistingPromotes verifies re-Putting an existing key refreshes recency,
// not just the value.
func TestPutExistingPromotes(t *testing.T) {
	c := NewLRU[string, int](2)
	c.Put("a", 1)
	c.Put("b", 2)
	c.Put("a", 10) // refresh a -> b becomes LRU
	c.Put("c", 3)  // evicts b

	if _, ok := c.Get("b"); ok {
		t.Fatalf("b still present; expected it evicted after a was refreshed")
	}
	if v, ok := c.Get("a"); !ok || v != 10 {
		t.Fatalf("a = (%d, %v), want (10, true)", v, ok)
	}
}

func TestCapacityOne(t *testing.T) {
	c := NewLRU[string, int](1)
	c.Put("a", 1)
	if v, ok := c.Get("a"); !ok || v != 1 {
		t.Fatalf("a = (%d, %v), want (1, true)", v, ok)
	}

	c.Put("b", 2) // evicts a immediately
	if _, ok := c.Get("a"); ok {
		t.Fatalf("a still present at capacity 1 after inserting b")
	}
	if v, ok := c.Get("b"); !ok || v != 2 {
		t.Fatalf("b = (%d, %v), want (2, true)", v, ok)
	}
	if c.Len() != 1 {
		t.Fatalf("Len() = %d at capacity 1, want 1", c.Len())
	}
}

// TestLRU_Concurrent_NoRace hammers Get/Put from many goroutines over an
// overlapping key space, with capacity < keyspace so evictions fire concurrently.
// Under `go test -race` this proves the baked-in Mutex guards the recency list; an
// RWMutex on Get (or no lock) would be flagged. It also asserts no torn values.
func TestLRU_Concurrent_NoRace(t *testing.T) {
	c := NewLRU[int, int](8) // cap 8 < keyspace 16 -> eviction churn

	const (
		goros    = 32
		ops      = 2000
		keyspace = 16
	)

	var wg sync.WaitGroup
	start := make(chan struct{})

	for g := 0; g < goros; g++ {
		wg.Add(1)
		go func(g int) {
			defer wg.Done()
			<-start
			for i := 0; i < ops; i++ {
				k := (g + i) % keyspace
				if i%2 == 0 {
					// every value written for key k satisfies v%keyspace == k
					c.Put(k, i*keyspace+k)
				} else if v, ok := c.Get(k); ok && v%keyspace != k {
					t.Errorf("torn read for key %d: got value %d", k, v)
					return
				}
			}
		}(g)
	}

	close(start)
	wg.Wait()
}
