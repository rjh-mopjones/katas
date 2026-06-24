package cache

import "testing"

func TestLFU_PutThenGet(t *testing.T) {
	c := NewLFU[string, int](2)
	c.Put("a", 1)
	if v, ok := c.Get("a"); !ok || v != 1 {
		t.Fatalf("Get(a) = (%d, %v), want (1, true)", v, ok)
	}
}

// TestLFU_EvictsLeastFrequentlyUsed: a is used more than b, so inserting a third
// key over capacity evicts b (the least frequently used), not a.
func TestLFU_EvictsLeastFrequentlyUsed(t *testing.T) {
	c := NewLFU[string, int](2)
	c.Put("a", 1)
	c.Put("b", 2)
	c.Get("a") // a freq=2, b freq=1
	c.Put("c", 3)

	if _, ok := c.Get("b"); ok {
		t.Fatalf("b still present; expected it evicted as least-frequently-used")
	}
	if v, ok := c.Get("a"); !ok || v != 1 {
		t.Fatalf("a = (%d, %v), want (1, true)", v, ok)
	}
	if v, ok := c.Get("c"); !ok || v != 3 {
		t.Fatalf("c = (%d, %v), want (3, true)", v, ok)
	}
}

// TestLFU_LRUTiebreakWithinFrequency: when all keys share the minimum frequency,
// the least-recently-used among them is evicted.
func TestLFU_LRUTiebreakWithinFrequency(t *testing.T) {
	c := NewLFU[string, int](2)
	c.Put("a", 1) // freq 1
	c.Put("b", 2) // freq 1
	c.Put("c", 3) // freq 1, over capacity -> evict LRU at minFreq, which is a

	if _, ok := c.Get("a"); ok {
		t.Fatalf("a still present; expected it evicted (LRU within minFreq bucket)")
	}
	if _, ok := c.Get("b"); !ok {
		t.Fatalf("b missing; expected it to survive")
	}
}

// TestLFU_UpdateExistingBumpsFrequency: updating a key counts as an access, so its
// frequency rises and it resists eviction.
func TestLFU_UpdateExistingBumpsFrequency(t *testing.T) {
	c := NewLFU[string, int](2)
	c.Put("a", 1)
	c.Put("b", 2)
	c.Put("a", 10) // a freq=2, value refreshed
	c.Put("c", 3)  // evict b (freq 1)

	if _, ok := c.Get("b"); ok {
		t.Fatalf("b still present; expected it evicted")
	}
	if v, ok := c.Get("a"); !ok || v != 10 {
		t.Fatalf("a = (%d, %v), want (10, true)", v, ok)
	}
}
