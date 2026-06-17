package messagebus

import (
	"fmt"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// TestBroker_RaceStress drives one Broker with several subscribers and many
// publishers across a few topics under maximum contention. Consumers ack most
// deliveries and nack-with-requeue some (forcing redelivery and, past the retry
// cap, dead-lettering), all concurrently with publishing. Run it with
// `go test -race -count=N`.
//
// # The no-message-lost invariant — and what it does NOT cover
//
// Delivery is at-least-once, so a single published message may surface as several
// Deliveries (redeliveries) and even as duplicates. The invariant we assert in
// aggregate is that no published message is LOST by the delivery machinery: the
// set of message IDs that were acked or dead-lettered must eventually cover every
// ID the broker accepted. Duplicates are allowed; ErrQueueFull shedding is allowed
// (those were never accepted, so not part of the obligation).
//
// Crucially we drain the broker to quiescence BEFORE calling Close, and only then
// assert coverage. Close has ABANDON semantics — it stops dispatch and discards
// whatever is still queued or in-flight-unacked (graceful drain is the separate
// `shutdown` kata's concern, not this broker's contract). If we asserted coverage
// after Close, messages legitimately abandoned in the queue would look "lost" when
// they were deliberately dropped at shutdown. Draining first separates a real
// delivery-loss bug (the watchdog fires because some accepted message never gets
// acked or dead-lettered) from correct shutdown abandonment. Close itself is still
// exercised under load — the consumer goroutines are mid-range when it fires, and
// the unsubscribes race it — so the detector still proves the shutdown path is
// race-free and never sends on a closed channel.
//
// No real sleeps gate logic: publishers/consumers coordinate through channels and
// the gate; the drain and leak checks poll on a short interval with a hard watchdog
// (the same shape as the goroutine-leak poll), which is waiting, not synchronizing.
func TestBroker_RaceStress(t *testing.T) {
	if testing.Short() {
		t.Skip("race-stress: run without -short, ideally -race -count=N")
	}

	baseline := runtime.NumGoroutine()

	const (
		topics    = 4
		perPub    = 200
		queueSize = 8
		prefetch  = 2
		maxRetry  = 2
	)
	nPublish := 6 * runtime.GOMAXPROCS(0)
	if nPublish < 16 {
		nPublish = 16
	}

	b := New(WithQueueSize(queueSize), WithPrefetch(prefetch), WithMaxRetries(maxRetry))

	topicName := func(i int) string { return fmt.Sprintf("t.%d", i) }

	// Acked-unique IDs are recorded in a shared sync.Map so the drain poll below can
	// read coverage concurrently while the consumers are still running — no plain map
	// shared across goroutines, nothing to race on.
	var acked sync.Map // id -> struct{}
	const consumers = topics
	var consumerWG sync.WaitGroup
	unsubs := make([]func(), consumers)

	for c := 0; c < consumers; c++ {
		c := c
		ch, unsub := b.Subscribe(topicName(c))
		unsubs[c] = unsub
		consumerWG.Add(1)
		go func() {
			defer consumerWG.Done()
			n := 0
			for d := range ch {
				n++
				// Nack-with-requeue roughly every 5th delivery (until the retry cap) so
				// messages are redelivered and, past the cap, dead-lettered. Every
				// message is eventually acked or dead-lettered, so coverage terminates.
				if n%5 == 0 && d.Redelivered <= maxRetry {
					d.Nack(true)
					continue
				}
				acked.Store(d.Message.ID, struct{}{})
				d.Ack()
			}
		}()
	}

	// Publishers: each publishes perPub messages spread across topics. We count only
	// messages the broker accepted (err == nil); ErrQueueFull is shed load.
	var published sync.Map // id -> struct{} (accepted)
	var acceptedN, shed int64
	start := make(chan struct{})
	var pubWG sync.WaitGroup
	for p := 0; p < nPublish; p++ {
		p := p
		pubWG.Add(1)
		go func() {
			defer pubWG.Done()
			<-start
			for i := 0; i < perPub; i++ {
				topic := topicName((p + i) % topics)
				id := fmt.Sprintf("m-%d-%d", p, i)
				err := b.Publish(topic, Message{ID: id, Topic: topic, Body: []byte(id)})
				switch err {
				case nil:
					published.Store(id, struct{}{})
					atomic.AddInt64(&acceptedN, 1)
				case ErrQueueFull:
					atomic.AddInt64(&shed, 1)
				default:
					t.Errorf("Publish %s: %v", id, err)
					return
				}
			}
		}()
	}

	close(start)
	pubWG.Wait()

	// coverage reports how many accepted IDs are covered (acked or dead-lettered).
	// Both reads are concurrency-safe: acked is a sync.Map, DeadLettered takes the
	// broker lock. Reading published (also a sync.Map) is safe after pubWG.Wait.
	coverage := func() (covered, missing int, sample []string) {
		covSet := make(map[string]struct{})
		acked.Range(func(k, _ any) bool { covSet[k.(string)] = struct{}{}; return true })
		for _, m := range b.DeadLettered() {
			covSet[m.ID] = struct{}{}
		}
		published.Range(func(k, _ any) bool {
			id := k.(string)
			if _, ok := covSet[id]; ok {
				covered++
			} else {
				missing++
				if len(sample) < 10 {
					sample = append(sample, id)
				}
			}
			return true
		})
		return covered, missing, sample
	}

	// Drain to quiescence: every accepted message must become acked or dead-lettered
	// while the broker is live. Poll with a hard watchdog — if it never reaches full
	// coverage, that is a genuine delivery-loss/hang bug, not shutdown abandonment.
	deadline := time.Now().Add(20 * time.Second)
	for {
		_, missing, sample := coverage()
		if missing == 0 {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("delivery loss: %d of %d accepted messages never acked or dead-lettered (e.g. %v, shed=%d)",
				missing, atomic.LoadInt64(&acceptedN), sample, atomic.LoadInt64(&shed))
		}
		time.Sleep(time.Millisecond)
	}

	// Now exercise Close under load: consumers are still ranging, unsubscribes race it.
	if err := b.Close(); err != nil {
		t.Fatalf("Close: %v", err)
	}
	for _, u := range unsubs {
		u() // idempotent no-op after Close — must not panic
	}
	consumerWG.Wait()

	if got := waitForGoroutines(baseline); got > baseline {
		t.Errorf("goroutine leak after Close: baseline %d, got %d", baseline, got)
	}
}
