package cache

import (
	"sync"
	"testing"
	"time"
)

// TestTTLLRU_ExpiresAfterTTL: the wrapper never serves a stale value. Driven by the
// injected fake clock so it is deterministic.
func TestTTLLRU_ExpiresAfterTTL(t *testing.T) {
	clk := newFakeClock()
	c := NewTTLLRU[string, int](2, 100*time.Millisecond, clk.Now)

	c.Put("a", 1)
	if v, ok := c.Get("a"); !ok || v != 1 {
		t.Fatalf("before expiry: Get(a) = (%d, %v), want (1, true)", v, ok)
	}

	clk.Advance(99 * time.Millisecond)
	if _, ok := c.Get("a"); !ok {
		t.Fatalf("at t=99ms: Get(a) ok = false, want true (not yet expired)")
	}

	clk.Advance(1 * time.Millisecond) // now == expiresAt counts as expired
	if _, ok := c.Get("a"); ok {
		t.Fatalf("at t=100ms: Get(a) ok = true, want false (TTL elapsed)")
	}
}

// TestTTLLRU_CapacityEvictionStillWorks: capacity bounding is inherited unchanged
// from the wrapped LRU. With a long TTL nothing expires, so this is pure LRU.
func TestTTLLRU_CapacityEvictionStillWorks(t *testing.T) {
	c := NewTTLLRU[string, int](2, time.Hour, nil) // nil clock -> time.Now

	c.Put("a", 1) // LRU
	c.Put("b", 2)
	c.Put("c", 3) // evicts a

	if _, ok := c.Get("a"); ok {
		t.Fatalf("a still present; expected capacity eviction of the LRU key")
	}
	if v, ok := c.Get("b"); !ok || v != 2 {
		t.Fatalf("b = (%d, %v), want (2, true)", v, ok)
	}
	if v, ok := c.Get("c"); !ok || v != 3 {
		t.Fatalf("c = (%d, %v), want (3, true)", v, ok)
	}
}

// TestTTLLRU_GetPromotesAcrossLayers: a Get through the wrapper promotes recency in
// the underlying LRU, so a touched key survives a subsequent eviction.
func TestTTLLRU_GetPromotesAcrossLayers(t *testing.T) {
	c := NewTTLLRU[string, int](2, time.Hour, nil)

	c.Put("a", 1)
	c.Put("b", 2)
	if _, ok := c.Get("a"); !ok { // promote a -> b becomes LRU
		t.Fatalf("precondition: Get(a) ok = false, want true")
	}
	c.Put("c", 3) // evicts b, not a

	if _, ok := c.Get("b"); ok {
		t.Fatalf("b still present; expected it evicted (a was promoted)")
	}
	if v, ok := c.Get("a"); !ok || v != 1 {
		t.Fatalf("a = (%d, %v), want (1, true)", v, ok)
	}
}

// TestTTLLRU_PutRefreshesTTL: re-writing a key resets its expiry window.
func TestTTLLRU_PutRefreshesTTL(t *testing.T) {
	clk := newFakeClock()
	c := NewTTLLRU[string, int](2, 100*time.Millisecond, clk.Now)

	c.Put("a", 1)
	clk.Advance(80 * time.Millisecond)
	c.Put("a", 2) // refresh: new deadline is now+100ms
	clk.Advance(80 * time.Millisecond)

	if v, ok := c.Get("a"); !ok || v != 2 {
		t.Fatalf("after refresh: Get(a) = (%d, %v), want (2, true) — TTL should have reset", v, ok)
	}
}

// TestTTLLRU_ExpiredEntryEvictedOnGet: because TTLLRU owns its own map and list, a
// Get that finds an expired entry removes it immediately (lazy eviction) — not just
// a reported miss. Len drops to 0.
func TestTTLLRU_ExpiredEntryEvictedOnGet(t *testing.T) {
	clk := newFakeClock()
	c := NewTTLLRU[string, int](2, 100*time.Millisecond, clk.Now)

	c.Put("a", 1)
	clk.Advance(200 * time.Millisecond)

	if _, ok := c.Get("a"); ok {
		t.Fatalf("Get(a) ok = true, want false (expired)")
	}
	if c.Len() != 0 {
		t.Fatalf("Len() = %d, want 0 — expired entry should be evicted on Get", c.Len())
	}
}

// TestTTLLRU_Concurrent_NoRace hammers Get/Put from many goroutines over an
// overlapping key space, with capacity < keyspace so evictions fire concurrently.
// Under `go test -race` this proves the baked-in Mutex actually guards the inner
// LRU's linked list — without it (or with an RWMutex on Get) the detector flags the
// concurrent recency mutation. It also asserts no torn values.
func TestTTLLRU_Concurrent_NoRace(t *testing.T) {
	c := NewTTLLRU[int, int](8, time.Hour, nil) // cap 8 < keyspace 16 -> eviction churn

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
					// Encode the key in the value so a read can verify it:
					// every value written for key k satisfies v%keyspace == k.
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
