package cache

import (
	"errors"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// fakeClock is a deterministic, race-safe time source for TTL tests. It lets us
// march time forward explicitly instead of sleeping, so expiry is exact and the
// suite stays fast.
type fakeClock struct {
	mu sync.Mutex
	t  time.Time
}

func newFakeClock() *fakeClock { return &fakeClock{t: time.Unix(0, 0)} }

func (f *fakeClock) Now() time.Time {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.t
}

func (f *fakeClock) Advance(d time.Duration) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.t = f.t.Add(d)
}

func TestSetThenGet(t *testing.T) {
	c := NewCache[string, int](time.Minute)
	defer c.Close()

	c.Set("a", 42)
	if got, ok := c.Get("a"); !ok || got != 42 {
		t.Fatalf("Get(a) = (%d, %v), want (42, true)", got, ok)
	}
}

func TestGetMissingKey(t *testing.T) {
	c := NewCache[string, int](time.Minute)
	defer c.Close()

	if got, ok := c.Get("nope"); ok || got != 0 {
		t.Fatalf("Get(nope) = (%d, %v), want (0, false)", got, ok)
	}
}

func TestSetOverwrites(t *testing.T) {
	c := NewCache[string, int](time.Minute)
	defer c.Close()

	c.Set("k", 1)
	c.Set("k", 2)
	if got, _ := c.Get("k"); got != 2 {
		t.Fatalf("Get(k) = %d, want 2", got)
	}
}

func TestDelete_TurnsHitIntoMiss(t *testing.T) {
	c := NewCache[string, int](time.Minute, WithSweepInterval(time.Hour))
	defer c.Close()

	c.Set("k", 1)
	if _, ok := c.Get("k"); !ok {
		t.Fatalf("precondition: Get(k) ok = false, want true")
	}
	if !c.Delete("k") {
		t.Fatalf("Delete(k) = false, want true (key was present)")
	}
	if _, ok := c.Get("k"); ok {
		t.Fatalf("after Delete: Get(k) ok = true, want false")
	}
	if c.Delete("k") {
		t.Fatalf("Delete(k) second time = true, want false (already gone)")
	}
}

func TestInvalidate_IsDelete(t *testing.T) {
	c := NewCache[string, int](time.Minute, WithSweepInterval(time.Hour))
	defer c.Close()

	c.Set("k", 1)
	if !c.Invalidate("k") {
		t.Fatalf("Invalidate(k) = false, want true")
	}
	if _, ok := c.Get("k"); ok {
		t.Fatalf("after Invalidate: Get(k) ok = true, want false")
	}
}

func TestClear_RemovesEverything(t *testing.T) {
	c := NewCache[string, int](time.Minute, WithSweepInterval(time.Hour))
	defer c.Close()

	c.Set("a", 1)
	c.Set("b", 2)
	if c.Len() != 2 {
		t.Fatalf("Len() = %d before Clear, want 2", c.Len())
	}
	c.Clear()
	if c.Len() != 0 {
		t.Fatalf("Len() = %d after Clear, want 0", c.Len())
	}
	if _, ok := c.Get("a"); ok {
		t.Fatalf("Get(a) ok = true after Clear, want false")
	}
}

// TestGet_ExpiresAfterTTL proves lazy expiry on the read path via the injected
// clock — once now reaches expiresAt, Get is a miss, no sweep needed. The sweeper
// is parked at 1h so only the Get-time check is exercised.
func TestGet_ExpiresAfterTTL(t *testing.T) {
	clk := newFakeClock()
	c := NewCache[string, int](100*time.Millisecond,
		WithClock(clk.Now),
		WithSweepInterval(time.Hour),
	)
	defer c.Close()

	c.Set("k", 7)
	if v, ok := c.Get("k"); !ok || v != 7 {
		t.Fatalf("before expiry: Get(k) = %d, %v; want 7, true", v, ok)
	}

	clk.Advance(99 * time.Millisecond)
	if _, ok := c.Get("k"); !ok {
		t.Fatalf("at t=99ms: Get(k) ok = false, want true (not yet expired)")
	}

	clk.Advance(1 * time.Millisecond) // now == expiresAt counts as expired
	if _, ok := c.Get("k"); ok {
		t.Fatalf("at t=100ms: Get(k) ok = true, want false (TTL elapsed)")
	}
}

// TestSweeper_EvictsExpired proves the background sweeper reclaims memory the lazy
// path leaves behind: the entry is still stored right after expiry, then dropped
// within a few ticks.
func TestSweeper_EvictsExpired(t *testing.T) {
	clk := newFakeClock()
	c := NewCache[string, int](10*time.Millisecond,
		WithClock(clk.Now),
		WithSweepInterval(2*time.Millisecond),
	)
	defer c.Close()

	c.Set("k", 1)
	if c.Len() != 1 {
		t.Fatalf("Len() = %d immediately after Set, want 1", c.Len())
	}

	clk.Advance(time.Second)

	deadline := time.Now().Add(2 * time.Second)
	for c.Len() != 0 {
		if time.Now().After(deadline) {
			t.Fatalf("sweeper did not evict expired entry: Len() = %d", c.Len())
		}
		time.Sleep(time.Millisecond)
	}
}

// TestClose_NoGoroutineLeak snapshots the goroutine count before/after and asserts
// the sweeper goroutine actually stops. A bounded poll absorbs the scheduling delay
// between Close and the goroutine returning.
func TestClose_NoGoroutineLeak(t *testing.T) {
	before := runtime.NumGoroutine()

	c := NewCache[string, int](50*time.Millisecond, WithSweepInterval(5*time.Millisecond))
	c.Set("a", 1)
	if got := runtime.NumGoroutine(); got <= before {
		t.Fatalf("expected sweeper goroutine running: NumGoroutine before=%d, during=%d", before, got)
	}

	if err := c.Close(); err != nil {
		t.Fatalf("Close() error = %v, want nil", err)
	}

	deadline := time.Now().Add(2 * time.Second)
	for runtime.NumGoroutine() > before {
		if time.Now().After(deadline) {
			t.Fatalf("goroutine leak after Close: before=%d, after=%d", before, runtime.NumGoroutine())
		}
		time.Sleep(time.Millisecond)
	}
}

func TestClose_Idempotent(t *testing.T) {
	c := NewCache[string, int](time.Minute, WithSweepInterval(time.Millisecond))
	if err := c.Close(); err != nil {
		t.Fatalf("first Close() error = %v", err)
	}
	if err := c.Close(); err != nil {
		t.Fatalf("second Close() error = %v, want nil (must be idempotent)", err)
	}
}

func TestGetOrCompute_ComputesAndCaches(t *testing.T) {
	c := NewCache[string, int](time.Minute, WithSweepInterval(time.Hour))
	defer c.Close()

	var calls int
	compute := func() (int, error) {
		calls++
		return 123, nil
	}

	if v, err := c.GetOrCompute("k", compute); err != nil || v != 123 {
		t.Fatalf("first GetOrCompute = %d, %v; want 123, nil", v, err)
	}
	if v, err := c.GetOrCompute("k", compute); err != nil || v != 123 {
		t.Fatalf("second GetOrCompute = %d, %v; want 123, nil", v, err)
	}
	if calls != 1 {
		t.Fatalf("compute ran %d times across two calls, want 1", calls)
	}
}

func TestGetOrCompute_ErrorNotCached(t *testing.T) {
	c := NewCache[string, int](time.Minute, WithSweepInterval(time.Hour))
	defer c.Close()

	boom := errors.New("boom")
	if _, err := c.GetOrCompute("k", func() (int, error) { return 0, boom }); !errors.Is(err, boom) {
		t.Fatalf("error path: err = %v, want boom", err)
	}
	if _, ok := c.Get("k"); ok {
		t.Fatalf("Get(k) ok = true after a failed compute; errors must not be cached")
	}
	if v, err := c.GetOrCompute("k", func() (int, error) { return 9, nil }); err != nil || v != 9 {
		t.Fatalf("recovery compute = %d, %v; want 9, nil", v, err)
	}
}

// TestGetOrCompute_NoStampede launches 100 goroutines that all miss the same key at
// once and asserts the compute fn ran exactly once. The compute sleeps so every
// goroutine piles into the singleflight slot before the first finishes.
func TestGetOrCompute_NoStampede(t *testing.T) {
	c := NewCache[string, int](time.Minute, WithSweepInterval(time.Hour))
	defer c.Close()

	var calls atomic.Int64
	const goros = 100

	var wg sync.WaitGroup
	start := make(chan struct{})
	results := make([]int, goros)
	errs := make([]error, goros)

	for i := 0; i < goros; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			<-start
			v, err := c.GetOrCompute("hot", func() (int, error) {
				calls.Add(1)
				time.Sleep(20 * time.Millisecond)
				return 99, nil
			})
			results[i] = v
			errs[i] = err
		}(i)
	}

	close(start)
	wg.Wait()

	if got := calls.Load(); got != 1 {
		t.Fatalf("compute fn ran %d times, want exactly 1 (cache stampede!)", got)
	}
	for i := 0; i < goros; i++ {
		if errs[i] != nil || results[i] != 99 {
			t.Fatalf("goroutine %d got (%d, %v), want (99, nil)", i, results[i], errs[i])
		}
	}
}

// TestConcurrent_GetSet_NoRace hammers Get/Set on overlapping keys with the sweeper
// churning. Under -race it flags any unsynchronised map access and asserts no torn
// values.
func TestConcurrent_GetSet_NoRace(t *testing.T) {
	c := NewCache[int, int](time.Minute, WithSweepInterval(time.Millisecond))
	defer c.Close()

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
					// Encode the key in the value so a read can verify it: every value
					// written for key k satisfies v%keyspace == k (k < keyspace).
					c.Set(k, i*keyspace+k)
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

// TestGetOrCompute_StoreUsesWriteLock pins the invariant that the store half of
// GetOrCompute's read-modify-write takes the full write Lock, never the RLock used
// on the read path. We cannot introspect the lock, so we prove it two ways: under
// -race, concurrent stores under an RLock are flagged; and correctness — every
// distinct key's computed value must survive (a lost update would show up below).
func TestGetOrCompute_StoreUsesWriteLock(t *testing.T) {
	c := NewCache[int, int](time.Minute, WithSweepInterval(time.Hour))
	defer c.Close()

	const goros = 64
	var wg sync.WaitGroup
	start := make(chan struct{})

	for k := 0; k < goros; k++ {
		wg.Add(1)
		go func(k int) {
			defer wg.Done()
			<-start
			v, err := c.GetOrCompute(k, func() (int, error) { return k * 10, nil })
			if err != nil || v != k*10 {
				t.Errorf("key %d: GetOrCompute = (%d, %v), want (%d, nil)", k, v, err, k*10)
			}
		}(k)
	}

	close(start)
	wg.Wait()

	for k := 0; k < goros; k++ {
		if v, ok := c.Get(k); !ok || v != k*10 {
			t.Fatalf("key %d missing/wrong after concurrent compute: got (%d, %v), want (%d, true)", k, v, ok, k*10)
		}
	}
}
