package messagebus

import (
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// waitForGoroutines polls runtime.NumGoroutine until it drops to at most want,
// returning the final observed count. The bounded poll only gives the scheduler
// time to reap goroutines that are already returning; it does not synchronise the
// logic under test.
func waitForGoroutines(want int) int {
	got := runtime.NumGoroutine()
	for i := 0; i < 200 && got > want; i++ {
		runtime.Gosched()
		time.Sleep(time.Millisecond)
		got = runtime.NumGoroutine()
	}
	return got
}

// recv receives one delivery or fails the test if none arrives promptly. The
// timeout is a generous deadlock guard, not a synchronisation device.
func recv(t *testing.T, ch <-chan Delivery) Delivery {
	t.Helper()
	select {
	case d, ok := <-ch:
		if !ok {
			t.Fatalf("delivery channel closed unexpectedly")
		}
		return d
	case <-time.After(2 * time.Second):
		t.Fatalf("timed out waiting for a delivery")
		return Delivery{}
	}
}

// expectNoDelivery asserts that no delivery arrives within a short window. Used to
// prove the prefetch cap holds back the next message.
func expectNoDelivery(t *testing.T, ch <-chan Delivery) {
	t.Helper()
	select {
	case d := <-ch:
		t.Fatalf("expected no delivery, got message %q", d.Message.ID)
	case <-time.After(50 * time.Millisecond):
	}
}

func TestPublish_RoutesToMatchingSubscriber(t *testing.T) {
	b := New(WithPrefetch(10))
	defer b.Close()

	ch, unsub := b.Subscribe("markets.football")
	defer unsub()

	if err := b.Publish("markets.football", Message{ID: "m1", Topic: "markets.football"}); err != nil {
		t.Fatalf("Publish: %v", err)
	}
	d := recv(t, ch)
	if d.Message.ID != "m1" {
		t.Fatalf("got %q, want m1", d.Message.ID)
	}
	if d.Redelivered != 1 {
		t.Fatalf("Redelivered = %d, want 1 on first delivery", d.Redelivered)
	}
}

func TestPublish_WildcardMatch(t *testing.T) {
	b := New(WithPrefetch(10))
	defer b.Close()

	ch, unsub := b.Subscribe("markets.*")
	defer unsub()

	// Matches: one trailing segment.
	if err := b.Publish("markets.tennis", Message{ID: "ok", Topic: "markets.tennis"}); err != nil {
		t.Fatalf("Publish: %v", err)
	}
	d := recv(t, ch)
	if d.Message.ID != "ok" {
		t.Fatalf("got %q, want ok", d.Message.ID)
	}

	// Does NOT match: two trailing segments, and the bare prefix.
	if err := b.Publish("markets.tennis.live", Message{ID: "deep", Topic: "markets.tennis.live"}); err != nil {
		t.Fatalf("Publish: %v", err)
	}
	if err := b.Publish("markets", Message{ID: "bare", Topic: "markets"}); err != nil {
		t.Fatalf("Publish: %v", err)
	}
	expectNoDelivery(t, ch)
}

func TestPublish_NonMatchingTopicNotDelivered(t *testing.T) {
	b := New(WithPrefetch(10))
	defer b.Close()

	ch, unsub := b.Subscribe("markets.football")
	defer unsub()

	if err := b.Publish("markets.tennis", Message{ID: "x", Topic: "markets.tennis"}); err != nil {
		t.Fatalf("Publish: %v", err)
	}
	expectNoDelivery(t, ch)
}

func TestAck_RemovesMessage_NoRedelivery(t *testing.T) {
	b := New(WithPrefetch(1), WithMaxRetries(5))
	defer b.Close()

	ch, unsub := b.Subscribe("t")
	defer unsub()

	b.Publish("t", Message{ID: "a", Topic: "t"})
	d := recv(t, ch)
	d.Ack()

	// After ack the slot frees, but there is nothing else to deliver and the acked
	// message must not come back.
	expectNoDelivery(t, ch)

	// A double-ack must be a harmless no-op (idempotent).
	d.Ack()
	d.Nack(true)
	expectNoDelivery(t, ch)
}

func TestNack_Requeue_RedeliversAndIncrementsCount(t *testing.T) {
	b := New(WithPrefetch(1), WithMaxRetries(5))
	defer b.Close()

	ch, unsub := b.Subscribe("t")
	defer unsub()

	b.Publish("t", Message{ID: "r", Topic: "t"})

	d1 := recv(t, ch)
	if d1.Redelivered != 1 {
		t.Fatalf("first delivery Redelivered = %d, want 1", d1.Redelivered)
	}
	d1.Nack(true)

	d2 := recv(t, ch)
	if d2.Message.ID != "r" {
		t.Fatalf("got %q, want r redelivered", d2.Message.ID)
	}
	if d2.Redelivered != 2 {
		t.Fatalf("redelivery Redelivered = %d, want 2", d2.Redelivered)
	}
	d2.Ack()
}

func TestPrefetch_CapsInFlightUntilAck(t *testing.T) {
	const prefetch = 2
	b := New(WithPrefetch(prefetch), WithMaxRetries(5), WithQueueSize(16))
	defer b.Close()

	ch, unsub := b.Subscribe("t")
	defer unsub()

	// Publish 3 messages; with prefetch=2 only 2 may be in flight.
	for _, id := range []string{"m1", "m2", "m3"} {
		if err := b.Publish("t", Message{ID: id, Topic: "t"}); err != nil {
			t.Fatalf("Publish %s: %v", id, err)
		}
	}

	d1 := recv(t, ch)
	d2 := recv(t, ch)
	if d1.Message.ID != "m1" || d2.Message.ID != "m2" {
		t.Fatalf("got %q,%q want m1,m2 (FIFO)", d1.Message.ID, d2.Message.ID)
	}

	// The 3rd must be withheld until a slot frees.
	expectNoDelivery(t, ch)

	// Ack one → exactly one new delivery is released.
	d1.Ack()
	d3 := recv(t, ch)
	if d3.Message.ID != "m3" {
		t.Fatalf("after ack got %q, want m3", d3.Message.ID)
	}
	// Still capped: m2 and m3 in flight, nothing more queued.
	expectNoDelivery(t, ch)
	d2.Ack()
	d3.Ack()
}

func TestDeadLetter_AfterMaxRetries(t *testing.T) {
	const maxRetries = 2
	b := New(WithPrefetch(1), WithMaxRetries(maxRetries))
	defer b.Close()

	ch, unsub := b.Subscribe("t")
	defer unsub()

	b.Publish("t", Message{ID: "poison", Topic: "t"})

	// Nack-with-requeue until the cap is exceeded. attempts run 1,2,3; the cap is 2,
	// so the delivery whose attempts==3 (> maxRetries) is the last one and is
	// dead-lettered on nack instead of being redelivered.
	for attempt := 1; attempt <= maxRetries+1; attempt++ {
		d := recv(t, ch)
		if d.Redelivered != attempt {
			t.Fatalf("attempt %d: Redelivered = %d, want %d", attempt, d.Redelivered, attempt)
		}
		d.Nack(true)
	}

	// No more deliveries: it is now poison.
	expectNoDelivery(t, ch)

	dl := b.DeadLettered()
	if len(dl) != 1 || dl[0].ID != "poison" {
		t.Fatalf("DeadLettered = %+v, want exactly [poison]", dl)
	}
}

func TestNack_NoRequeue_DeadLettersImmediately(t *testing.T) {
	b := New(WithPrefetch(1), WithMaxRetries(5))
	defer b.Close()

	ch, unsub := b.Subscribe("t")
	defer unsub()

	b.Publish("t", Message{ID: "drop", Topic: "t"})
	d := recv(t, ch)
	d.Nack(false) // explicit reject without requeue

	expectNoDelivery(t, ch)
	dl := b.DeadLettered()
	if len(dl) != 1 || dl[0].ID != "drop" {
		t.Fatalf("DeadLettered = %+v, want [drop]", dl)
	}
}

func TestAckTimeout_RedeliversViaInjectedClock(t *testing.T) {
	var nowNanos atomic.Int64
	base := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	nowNanos.Store(base.UnixNano())
	clock := func() time.Time { return time.Unix(0, nowNanos.Load()) }

	b := New(
		WithPrefetch(1),
		WithMaxRetries(5),
		WithAckTimeout(30*time.Second),
		WithClock(clock),
	)
	defer b.Close()

	ch, unsub := b.Subscribe("t")
	defer unsub()

	b.Publish("t", Message{ID: "slow", Topic: "t"})

	d1 := recv(t, ch)
	if d1.Redelivered != 1 {
		t.Fatalf("first Redelivered = %d, want 1", d1.Redelivered)
	}
	// Deliberately do NOT ack. Advance the fake clock past the ack-timeout.
	nowNanos.Store(base.Add(31 * time.Second).UnixNano())

	d2 := recv(t, ch)
	if d2.Message.ID != "slow" || d2.Redelivered != 2 {
		t.Fatalf("after timeout got id=%q Redelivered=%d, want slow/2", d2.Message.ID, d2.Redelivered)
	}

	// The original (timed-out) delivery's late ack must be a no-op; only the live
	// delivery's ack counts.
	d1.Ack()
	d2.Ack()
	expectNoDelivery(t, ch)
}

func TestFIFO_OrderingWithinQueue(t *testing.T) {
	b := New(WithPrefetch(1), WithQueueSize(64))
	defer b.Close()

	ch, unsub := b.Subscribe("t")
	defer unsub()

	const n = 20
	for i := 0; i < n; i++ {
		b.Publish("t", Message{ID: string(rune('a' + i)), Topic: "t"})
	}
	for i := 0; i < n; i++ {
		d := recv(t, ch)
		want := string(rune('a' + i))
		if d.Message.ID != want {
			t.Fatalf("position %d: got %q want %q (FIFO violated)", i, d.Message.ID, want)
		}
		d.Ack()
	}
}

func TestPublish_QueueFull_ReturnsErr(t *testing.T) {
	b := New(WithPrefetch(1), WithQueueSize(2))
	defer b.Close()

	_, unsub := b.Subscribe("t")
	defer unsub()

	// First two fit (capacity 2); the third overflows. We do not receive, so the
	// dispatched-but-unacked message plus the queued one fill capacity.
	_ = b.Publish("t", Message{ID: "1", Topic: "t"})
	_ = b.Publish("t", Message{ID: "2", Topic: "t"})
	if err := b.Publish("t", Message{ID: "3", Topic: "t"}); err != ErrQueueFull {
		t.Fatalf("third Publish: got %v, want ErrQueueFull", err)
	}
}

func TestClose_Idempotent_ClosesChannels(t *testing.T) {
	b := New(WithPrefetch(4))
	ch, _ := b.Subscribe("t")

	if err := b.Close(); err != nil {
		t.Fatalf("Close: %v", err)
	}
	if err := b.Close(); err != nil {
		t.Fatalf("second Close: %v", err) // idempotent, must not panic
	}

	// Delivery channel must be closed.
	select {
	case _, ok := <-ch:
		if ok {
			t.Fatalf("expected closed delivery channel")
		}
	case <-time.After(2 * time.Second):
		t.Fatalf("delivery channel not closed after Close")
	}

	// Publish/Subscribe after Close are clean errors, not panics.
	if err := b.Publish("t", Message{ID: "late", Topic: "t"}); err != ErrClosed {
		t.Fatalf("Publish after Close: got %v, want ErrClosed", err)
	}
	dead, unsub := b.Subscribe("t")
	unsub()
	select {
	case _, ok := <-dead:
		if ok {
			t.Fatalf("Subscribe after Close should return a closed channel")
		}
	case <-time.After(2 * time.Second):
		t.Fatalf("Subscribe-after-Close channel not closed")
	}
}

func TestClose_ConcurrentPublishSubscribe_NoPanic_NoLeak(t *testing.T) {
	baseline := waitForGoroutines(runtime.NumGoroutine())

	b := New(WithPrefetch(8), WithQueueSize(32))

	// Drain any deliveries so dispatchers are never wedged on a full out channel.
	var subWG sync.WaitGroup
	drain := func(ch <-chan Delivery) {
		defer subWG.Done()
		for d := range ch {
			d.Ack()
		}
	}
	for i := 0; i < 4; i++ {
		ch, _ := b.Subscribe("markets.*")
		subWG.Add(1)
		go drain(ch)
	}

	start := make(chan struct{})
	var wg sync.WaitGroup

	// Publishers.
	const publishers = 8
	wg.Add(publishers)
	for i := 0; i < publishers; i++ {
		go func() {
			defer wg.Done()
			<-start
			for j := 0; j < 100; j++ {
				err := b.Publish("markets.football", Message{ID: "x", Topic: "markets.football"})
				if err != nil && err != ErrClosed && err != ErrQueueFull {
					t.Errorf("Publish unexpected error: %v", err)
				}
			}
		}()
	}

	// Subscribers churning during the race.
	const subscribers = 4
	wg.Add(subscribers)
	for i := 0; i < subscribers; i++ {
		go func() {
			defer wg.Done()
			<-start
			for j := 0; j < 20; j++ {
				ch, unsub := b.Subscribe("markets.*")
				subWG.Add(1)
				go drain(ch)
				unsub()
			}
		}()
	}

	close(start)
	// Close races the in-flight publishers/subscribers.
	if err := b.Close(); err != nil {
		t.Fatalf("Close: %v", err)
	}
	wg.Wait()
	subWG.Wait() // all drain goroutines saw their channel close.

	if got := waitForGoroutines(baseline); got > baseline {
		t.Fatalf("goroutine leak: baseline=%d, after=%d", baseline, got)
	}
}

func TestIdempotentConsumer_DedupesRedelivery(t *testing.T) {
	// Demonstrates the money-angle pattern the kata teaches: at-least-once delivery
	// means a redelivered "settle bet" must be deduped on Message.ID so it pays once.
	b := New(WithPrefetch(1), WithMaxRetries(5))
	defer b.Close()

	ch, unsub := b.Subscribe("settle")
	defer unsub()

	var payouts atomic.Int64
	seen := map[string]bool{}

	process := func(d Delivery) {
		if seen[d.Message.ID] {
			d.Ack() // already settled; just ack the duplicate
			return
		}
		payouts.Add(1)
		seen[d.Message.ID] = true
		d.Ack()
	}

	b.Publish("settle", Message{ID: "bet-42", Topic: "settle"})

	// First delivery: pretend the consumer crashed AFTER doing work but BEFORE ack,
	// so it nacks-requeue → redelivery.
	d1 := recv(t, ch)
	// simulate having done the work then losing the ack: record it, then nack.
	payouts.Add(1)
	seen[d1.Message.ID] = true
	d1.Nack(true)

	// Redelivery: the idempotent consumer must NOT pay again.
	d2 := recv(t, ch)
	process(d2)

	if got := payouts.Load(); got != 1 {
		t.Fatalf("payouts = %d, want 1 (idempotent consumer must dedupe redelivery)", got)
	}
}
