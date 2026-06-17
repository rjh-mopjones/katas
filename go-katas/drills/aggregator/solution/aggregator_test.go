package aggregator

import (
	"context"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// fakeClock is an atomic-backed clock the tests advance deterministically. No
// real sleeps anywhere in this suite — staleness is driven purely by Advance.
type fakeClock struct{ ns atomic.Int64 }

func newFakeClock(t time.Time) *fakeClock {
	c := &fakeClock{}
	c.ns.Store(t.UnixNano())
	return c
}
func (c *fakeClock) Now() time.Time          { return time.Unix(0, c.ns.Load()) }
func (c *fakeClock) Advance(d time.Duration) { c.ns.Add(int64(d)) }

func base() time.Time { return time.Unix(1_700_000_000, 0) }

// --- Stage 0: core best-of-book logic, table-driven -------------------------

func TestBestOfBook(t *testing.T) {
	clk := newFakeClock(base())
	tests := []struct {
		name      string
		updates   []PriceUpdate
		market    string
		wantOK    bool
		wantBack  float64
		wantBackV string
		wantLay   float64
		wantLayV  string
	}{
		{
			name:   "unknown market",
			market: "missing",
			wantOK: false,
		},
		{
			name:     "single venue",
			updates:  []PriceUpdate{{Venue: "A", Market: "m", Back: 2.0, Lay: 2.1, Ts: base()}},
			market:   "m",
			wantOK:   true,
			wantBack: 2.0, wantBackV: "A",
			wantLay: 2.1, wantLayV: "A",
		},
		{
			name: "best back is highest, best lay is lowest",
			updates: []PriceUpdate{
				{Venue: "A", Market: "m", Back: 2.0, Lay: 2.2, Ts: base()},
				{Venue: "B", Market: "m", Back: 2.5, Lay: 2.1, Ts: base()},
				{Venue: "C", Market: "m", Back: 2.3, Lay: 2.4, Ts: base()},
			},
			market:   "m",
			wantOK:   true,
			wantBack: 2.5, wantBackV: "B",
			wantLay: 2.1, wantLayV: "B",
		},
		{
			name: "overwrite same venue replaces, not accumulates",
			updates: []PriceUpdate{
				{Venue: "A", Market: "m", Back: 9.0, Lay: 1.0, Ts: base()},
				{Venue: "A", Market: "m", Back: 2.0, Lay: 2.0, Ts: base()},
			},
			market:   "m",
			wantOK:   true,
			wantBack: 2.0, wantBackV: "A",
			wantLay: 2.0, wantLayV: "A",
		},
		{
			name: "best back and best lay can come from different venues",
			updates: []PriceUpdate{
				{Venue: "A", Market: "m", Back: 3.0, Lay: 3.5, Ts: base()},
				{Venue: "B", Market: "m", Back: 2.0, Lay: 2.5, Ts: base()},
			},
			market:   "m",
			wantOK:   true,
			wantBack: 3.0, wantBackV: "A",
			wantLay: 2.5, wantLayV: "B",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			a := New(WithClock(clk.Now), WithTTL(time.Minute))
			defer a.Close()
			for _, u := range tt.updates {
				a.Apply(u)
			}
			v, ok := a.Get(tt.market)
			if ok != tt.wantOK {
				t.Fatalf("ok = %v, want %v", ok, tt.wantOK)
			}
			if !tt.wantOK {
				return
			}
			if v.Back != tt.wantBack || v.BackVenue != tt.wantBackV {
				t.Errorf("back = %v@%q, want %v@%q", v.Back, v.BackVenue, tt.wantBack, tt.wantBackV)
			}
			if v.Lay != tt.wantLay || v.LayVenue != tt.wantLayV {
				t.Errorf("lay = %v@%q, want %v@%q", v.Lay, v.LayVenue, tt.wantLay, tt.wantLayV)
			}
		})
	}
}

// --- Stage 3: staleness with an injectable fake clock -----------------------

func TestStalenessExpiresAndRevives(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(10*time.Second), WithSweepInterval(time.Hour))
	defer a.Close()

	a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 2.0, Lay: 2.1, Ts: clk.Now()})
	if _, ok := a.Get("m"); !ok {
		t.Fatal("fresh quote should be served")
	}

	// Advance past the TTL: Get must drop the stale side and report nothing.
	clk.Advance(11 * time.Second)
	if v, ok := a.Get("m"); ok {
		t.Fatalf("stale quote should not be served, got %+v", v)
	}

	// A fresh update revives the market.
	a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 3.0, Lay: 3.1, Ts: clk.Now()})
	v, ok := a.Get("m")
	if !ok || v.Back != 3.0 {
		t.Fatalf("revived quote should be served, got %+v ok=%v", v, ok)
	}
}

func TestStalenessOneSideExpires(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(10*time.Second), WithSweepInterval(time.Hour))
	defer a.Close()

	a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 2.0, Lay: 0, Ts: clk.Now()})
	clk.Advance(5 * time.Second)
	a.Apply(PriceUpdate{Venue: "B", Market: "m", Back: 0, Lay: 2.1, Ts: clk.Now()})

	// Advance so A's back is stale but B's lay is still fresh.
	clk.Advance(6 * time.Second)
	v, ok := a.Get("m")
	if !ok {
		t.Fatal("lay side should still be fresh")
	}
	if v.Back != 0 {
		t.Errorf("back should have expired, got %v", v.Back)
	}
	if v.Lay != 2.1 {
		t.Errorf("lay should remain, got %v", v.Lay)
	}
}

func TestSweeperExpiresWithoutUpdates(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(10*time.Second), WithSweepInterval(5*time.Millisecond))
	defer a.Close()

	a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 2.0, Lay: 2.1, Ts: clk.Now()})
	clk.Advance(20 * time.Second)

	// The sweeper should republish an empty view even though no update arrived.
	// Get already drops stale sides, so we assert the published snapshot is empty
	// by polling the atomic view directly (the sweeper's effect, not Get's).
	m := a.lookup("m")
	ok := pollUntil(200*time.Millisecond, func() bool {
		snap := m.view.Load()
		return snap != nil && snap.Back == 0 && snap.Lay == 0
	})
	if !ok {
		t.Fatal("sweeper should have republished an empty view")
	}
}

// --- Stage 1+2: concurrency under -race -------------------------------------

func TestConcurrentApplyAndGetRace(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Millisecond))
	defer a.Close()

	const writers, readers, perWriter = 8, 8, 2000
	markets := []string{"m0", "m1", "m2", "m3"}

	start := make(chan struct{})
	var wg sync.WaitGroup

	for w := 0; w < writers; w++ {
		wg.Add(1)
		go func(w int) {
			defer wg.Done()
			<-start
			for i := 0; i < perWriter; i++ {
				mkt := markets[i%len(markets)]
				a.Apply(PriceUpdate{
					Venue:  "v" + string(rune('A'+w)),
					Market: mkt,
					Back:   1 + float64(i%50),
					Lay:    100 + float64(i%50),
					Ts:     clk.Now(),
				})
			}
		}(w)
	}
	for r := 0; r < readers; r++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			for i := 0; i < perWriter; i++ {
				_, _ = a.Get(markets[i%len(markets)])
			}
		}()
	}

	close(start)
	wg.Wait()

	// Deterministic invariant: feed a known final set and assert the best.
	clk2 := clk.Now()
	a.Apply(PriceUpdate{Venue: "X", Market: "final", Back: 5.0, Lay: 6.0, Ts: clk2})
	a.Apply(PriceUpdate{Venue: "Y", Market: "final", Back: 7.0, Lay: 4.0, Ts: clk2})
	v, ok := a.Get("final")
	if !ok || v.Back != 7.0 || v.Lay != 4.0 {
		t.Fatalf("final = %+v ok=%v, want back 7 lay 4", v, ok)
	}
}

// --- Stage 4+5: subscriptions, isolation, backpressure ----------------------

func TestSubscribeReceivesUpdates(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Hour))
	defer a.Close()

	ch, unsub := a.Subscribe(context.Background(), "m")
	defer unsub()

	a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 2.0, Lay: 2.1, Ts: clk.Now()})
	select {
	case v := <-ch:
		if v.Back != 2.0 {
			t.Fatalf("got %+v", v)
		}
	case <-time.After(time.Second):
		t.Fatal("expected pushed view")
	}
}

func TestSlowSubscriberDoesNotBlockOthersOrIngestion(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Hour),
		WithSubscriberBuffer(1))
	defer a.Close()

	// Slow subscriber: never drains.
	slow, unsubSlow := a.Subscribe(context.Background(), "m")
	defer unsubSlow()
	_ = slow

	// Healthy subscriber: drains and must see the latest price.
	healthy, unsubHealthy := a.Subscribe(context.Background(), "m")
	defer unsubHealthy()

	done := make(chan struct{})
	go func() {
		// Ingestion must complete quickly despite the stuck slow subscriber.
		for i := 0; i < 1000; i++ {
			a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: float64(i), Lay: 1, Ts: clk.Now()})
		}
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("ingestion blocked by slow subscriber")
	}

	// Healthy subscriber, draining, should eventually observe the freshest back
	// (999) thanks to latest-wins coalescing.
	deadline := time.After(2 * time.Second)
	var last float64
	for last != 999 {
		select {
		case v := <-healthy:
			last = v.Back
		case <-deadline:
			t.Fatalf("healthy subscriber never saw latest, last=%v", last)
		}
	}
}

func TestUnsubscribeClosesChannelAndStopsDelivery(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Hour))
	defer a.Close()

	ch, unsub := a.Subscribe(context.Background(), "m")
	unsub()

	// Channel must be closed (drains to zero value, ok=false).
	if _, ok := <-ch; ok {
		t.Fatal("channel should be closed after unsubscribe")
	}
	// Further applies must not panic (no send on closed channel).
	a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 2.0, Lay: 2.1, Ts: clk.Now()})

	// Idempotent unsubscribe.
	unsub()
}

func TestContextCancelEndsSubscription(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Hour))
	defer a.Close()

	ctx, cancel := context.WithCancel(context.Background())
	ch, _ := a.Subscribe(ctx, "m")
	cancel()

	select {
	case _, ok := <-ch:
		if ok {
			t.Fatal("expected closed channel after ctx cancel")
		}
	case <-time.After(time.Second):
		t.Fatal("ctx cancel did not close subscription")
	}
}

// --- Stage 6: graceful shutdown, no leaks -----------------------------------

func TestCloseClosesSubscribersAndIsIdempotent(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Hour))

	ch, _ := a.Subscribe(context.Background(), "m")
	if err := a.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}
	if _, ok := <-ch; ok {
		t.Fatal("subscriber channel should be closed by Close")
	}
	// Apply after Close is a no-op, no panic.
	a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 1, Lay: 1, Ts: clk.Now()})
	// Idempotent Close.
	if err := a.Close(); err != nil {
		t.Fatalf("second close: %v", err)
	}
}

func TestConcurrentApplySubscribeDuringClose(t *testing.T) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Millisecond))

	var wg sync.WaitGroup
	for i := 0; i < 16; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 200; j++ {
				a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: 1, Lay: 1, Ts: clk.Now()})
				ch, unsub := a.Subscribe(context.Background(), "m")
				_ = ch
				unsub()
			}
		}()
	}
	// Close racing with the workers must not panic.
	time.Sleep(time.Millisecond)
	if err := a.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}
	wg.Wait()
}

func TestNoGoroutineLeak(t *testing.T) {
	clk := newFakeClock(base())
	baseline := runtime.NumGoroutine()

	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Millisecond))
	for i := 0; i < 50; i++ {
		ctx, cancel := context.WithCancel(context.Background())
		_, unsub := a.Subscribe(ctx, "m")
		if i%2 == 0 {
			unsub()
			cancel() // exercise unsubscribe path; cancel just releases ctx
		} else {
			cancel() // exercise ctx-cancel cleanup path
			unsub()
		}
	}
	if err := a.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}

	// Poll for goroutine count to return to baseline (polling is fine — it's not
	// logic synchronization, just waiting for watchers/sweeper to unwind).
	ok := pollUntil(time.Second, func() bool {
		return runtime.NumGoroutine() <= baseline+1
	})
	if !ok {
		t.Fatalf("goroutine leak: have %d, baseline %d", runtime.NumGoroutine(), baseline)
	}
}

// pollUntil retries cond every ~1ms until it's true or the timeout elapses.
func pollUntil(timeout time.Duration, cond func() bool) bool {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		if cond() {
			return true
		}
		runtime.Gosched()
		time.Sleep(time.Millisecond)
	}
	return cond()
}

// --- Stage 7: read-path benchmark -------------------------------------------

func BenchmarkGet(b *testing.B) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Hour))
	defer a.Close()
	for i := 0; i < 64; i++ {
		a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: float64(i), Lay: float64(i + 1), Ts: clk.Now()})
	}
	b.ReportAllocs()
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		for pb.Next() {
			_, _ = a.Get("m")
		}
	})
}

func BenchmarkApply(b *testing.B) {
	clk := newFakeClock(base())
	a := New(WithClock(clk.Now), WithTTL(time.Hour), WithSweepInterval(time.Hour))
	defer a.Close()
	b.ReportAllocs()
	b.ResetTimer()
	b.RunParallel(func(pb *testing.PB) {
		i := 0
		for pb.Next() {
			a.Apply(PriceUpdate{Venue: "A", Market: "m", Back: float64(i), Lay: 1, Ts: clk.Now()})
			i++
		}
	})
}
