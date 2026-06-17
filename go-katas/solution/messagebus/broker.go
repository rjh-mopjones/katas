package messagebus

import (
	"strings"
	"sync"
	"time"
)

// Default configuration applied before functional Options.
const (
	defaultPrefetch   = 1
	defaultMaxRetries = 3
	defaultQueueSize  = 64
)

// Option configures a Broker at construction time (the functional-options
// pattern). Unknown future knobs can be added without breaking New's signature.
type Option func(*config)

type config struct {
	prefetch   int
	maxRetries int
	queueSize  int
	ackTimeout time.Duration
	now        func() time.Time
}

// WithPrefetch sets the per-consumer QoS prefetch count: the maximum number of
// unacked deliveries outstanding to a single consumer before the queue stops
// dispatching. n < 1 is clamped to 1. This is the broker's backpressure knob.
func WithPrefetch(n int) Option {
	return func(c *config) {
		if n < 1 {
			n = 1
		}
		c.prefetch = n
	}
}

// WithMaxRetries sets how many times a message may be redelivered before it is
// dead-lettered. A message dispatched maxRetries+1 times (i.e. exceeding the cap)
// goes to the DLQ instead of being redelivered again. n < 0 is clamped to 0
// (dead-letter on the first nack-requeue).
func WithMaxRetries(n int) Option {
	return func(c *config) {
		if n < 0 {
			n = 0
		}
		c.maxRetries = n
	}
}

// WithQueueSize sets the bounded capacity of each subscription's queue. Publish to
// a full queue returns ErrQueueFull (shed-on-full policy). n < 1 is clamped to 1.
func WithQueueSize(n int) Option {
	return func(c *config) {
		if n < 1 {
			n = 1
		}
		c.queueSize = n
	}
}

// WithAckTimeout sets the duration after which an unacked delivery is treated as a
// nack-with-requeue (redelivered, or dead-lettered once the retry cap is hit). A
// zero duration (the default) disables ack-timeouts. Time is read through the
// injected clock (WithClock), so tests advance a fake clock rather than sleeping.
func WithAckTimeout(d time.Duration) Option {
	return func(c *config) { c.ackTimeout = d }
}

// WithClock injects the time source used for ack-timeout accounting. The default
// is time.Now. Tests pass a controllable clock so ack-timeout redelivery is
// deterministic without real sleeps.
func WithClock(now func() time.Time) Option {
	return func(c *config) {
		if now != nil {
			c.now = now
		}
	}
}

// subscription pairs a queue with the topic it is bound to and its unsubscribe
// guard, so the broker can route by topic and tear down cleanly.
type subscription struct {
	topic     string
	q         *queue
	closeOnce sync.Once
}

// Broker is an in-memory message broker with topic routing, per-subscription
// bounded queues, manual ack/nack with redelivery, prefetch (QoS), and
// dead-lettering. It is safe for concurrent use; construct it with New.
//
// Routing collapses AMQP's exchange+binding into Publish: a message's Topic is the
// routing key, and it is copied into every subscription whose bound topic matches
// (see Publish for the matching rule). Each Subscribe owns one queue feeding one
// delivery channel.
type Broker struct {
	cfg config

	mu     sync.RWMutex
	subs   map[*subscription]struct{}
	closed bool

	closeOnce sync.Once
}

// New returns a ready Broker configured by the given options.
func New(opts ...Option) *Broker {
	cfg := config{
		prefetch:   defaultPrefetch,
		maxRetries: defaultMaxRetries,
		queueSize:  defaultQueueSize,
		now:        time.Now,
	}
	for _, opt := range opts {
		opt(&cfg)
	}
	return &Broker{
		cfg:  cfg,
		subs: make(map[*subscription]struct{}),
	}
}

// Publish routes m to every subscription whose bound topic matches m.Topic,
// returning the first routing error (ErrClosed, or ErrQueueFull from a full
// destination queue). It returns nil if the message routed to all matching queues
// (including the case of zero matches — publishing to a topic nobody is listening
// on is not an error, mirroring an AMQP exchange with no matching binding).
//
// Matching rule (a deliberately small subset of AMQP topic exchanges):
//
//   - Exact match: subscription topic == m.Topic.
//   - Single-level trailing wildcard: a subscription topic ending in ".*" matches
//     any m.Topic with the same prefix and exactly one more dot-separated segment.
//     "markets.*" matches "markets.football" and "markets.tennis" but NOT
//     "markets" (no trailing segment) nor "markets.football.fulltime" (two extra
//     segments).
//
// A message is copied to each matching queue independently; one queue being full
// (ErrQueueFull) does not stop delivery to the others, but the first such error is
// returned so the caller learns the publish was not fully satisfied.
func (b *Broker) Publish(topic string, m Message) error {
	b.mu.RLock()
	if b.closed {
		b.mu.RUnlock()
		return ErrClosed
	}
	// Snapshot the matching queues under the read lock, then publish outside it so a
	// slow queue cannot stall other publishers holding the lock.
	var targets []*queue
	for s := range b.subs {
		if topicMatches(s.topic, m.Topic) {
			targets = append(targets, s.q)
		}
	}
	b.mu.RUnlock()

	var firstErr error
	for _, q := range targets {
		if err := q.publish(m); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}

// Subscribe binds a new queue to topic and returns its delivery channel plus an
// idempotent unsubscribe function. The channel is owned by the broker and closed
// exactly once — by unsubscribe or by Close — never by the caller. Consumers must
// Ack or Nack every Delivery; the prefetch cap limits how many are outstanding at
// once.
//
// If the broker is already closed, Subscribe returns a closed channel and a no-op
// unsubscribe, so a consumer ranging over the channel terminates immediately
// rather than blocking forever.
func (b *Broker) Subscribe(topic string, opts ...Option) (<-chan Delivery, func()) {
	b.mu.Lock()
	if b.closed {
		b.mu.Unlock()
		dead := make(chan Delivery)
		close(dead)
		return dead, func() {}
	}

	// Per-subscription overrides start from the broker config.
	cfg := b.cfg
	for _, opt := range opts {
		opt(&cfg)
	}
	q := newQueue(cfg.queueSize, cfg.prefetch, cfg.maxRetries, cfg.ackTimeout, cfg.now)
	s := &subscription{topic: topic, q: q}
	b.subs[s] = struct{}{}
	b.mu.Unlock()

	unsubscribe := func() {
		s.closeOnce.Do(func() {
			b.mu.Lock()
			delete(b.subs, s)
			b.mu.Unlock()
			q.close()
		})
	}
	return q.out, unsubscribe
}

// DeadLettered returns a snapshot of every dead-lettered message across all live
// subscriptions — the poison messages that exceeded their redelivery cap or were
// nacked without requeue. It is the analogue of inspecting a RabbitMQ dead-letter
// exchange's queue.
func (b *Broker) DeadLettered() []Message {
	b.mu.RLock()
	defer b.mu.RUnlock()
	var out []Message
	for s := range b.subs {
		out = append(out, s.q.deadLettered()...)
	}
	return out
}

// Close shuts the broker down: it stops every queue's dispatcher, closes every
// delivery channel exactly once, and rejects further Publish/Subscribe with
// ErrClosed. It is idempotent (guarded by sync.Once) and leaves no goroutine
// leaked. Concurrent Publish/Subscribe racing Close observe ErrClosed or a closed
// channel — never a panic.
func (b *Broker) Close() error {
	b.closeOnce.Do(func() {
		b.mu.Lock()
		b.closed = true
		subs := make([]*subscription, 0, len(b.subs))
		for s := range b.subs {
			subs = append(subs, s)
		}
		b.subs = make(map[*subscription]struct{})
		b.mu.Unlock()

		// Close queues outside the lock; each close waits for its dispatcher to exit
		// and closes its out channel exactly once.
		for _, s := range subs {
			s.closeOnce.Do(func() { s.q.close() })
		}
	})
	return nil
}

// topicMatches reports whether a subscription's bound topic matches a published
// routing key, per the rule documented on Publish.
func topicMatches(bound, key string) bool {
	if bound == key {
		return true
	}
	if !strings.HasSuffix(bound, ".*") {
		return false
	}
	prefix := strings.TrimSuffix(bound, ".*") // "markets" from "markets.*"
	if !strings.HasPrefix(key, prefix+".") {
		return false
	}
	rest := key[len(prefix)+1:] // the single segment after the prefix dot
	return rest != "" && !strings.Contains(rest, ".")
}
