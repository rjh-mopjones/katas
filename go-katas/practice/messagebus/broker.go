package messagebus

import (
	"time"
)

// Option configures a Broker at construction time (the functional-options pattern).
type Option func(*config)

// config holds the resolved broker settings. Design its fields as you implement.
type config struct{}

// WithPrefetch sets the per-consumer QoS prefetch count: the maximum number of
// unacked deliveries outstanding to a single consumer before the queue stops
// dispatching. This is the broker's backpressure knob.
func WithPrefetch(n int) Option {
	panic("TODO: implement")
}

// WithMaxRetries sets how many times a message may be redelivered before it is
// dead-lettered.
func WithMaxRetries(n int) Option {
	panic("TODO: implement")
}

// WithQueueSize sets the bounded capacity of each subscription's queue.
func WithQueueSize(n int) Option {
	panic("TODO: implement")
}

// WithAckTimeout sets the duration after which an unacked delivery is treated as a
// nack-with-requeue. A zero duration disables ack-timeouts. Time is read through
// the injected clock (WithClock).
func WithAckTimeout(d time.Duration) Option {
	panic("TODO: implement")
}

// WithClock injects the time source used for ack-timeout accounting (default
// time.Now). Tests pass a controllable clock so ack-timeout redelivery is
// deterministic without real sleeps.
func WithClock(now func() time.Time) Option {
	panic("TODO: implement")
}

// Broker is an in-memory message broker with topic routing, per-subscription
// bounded queues, manual ack/nack with redelivery, prefetch (QoS), and
// dead-lettering. It must be safe for concurrent use; construct it with New.
type Broker struct{}

// New returns a ready Broker configured by the given options.
func New(opts ...Option) *Broker {
	panic("TODO: implement")
}

// Publish routes m to every subscription whose bound topic matches m.Topic.
//
// Matching rule: exact match, or a single-level trailing wildcard — a subscription
// topic ending in ".*" matches any m.Topic with the same prefix and exactly one
// more dot-separated segment ("markets.*" matches "markets.football" but not
// "markets" nor "markets.football.fulltime"). Publishing to a topic nobody listens
// on is not an error. Returns ErrClosed if closed, ErrQueueFull if a destination
// queue is full.
func (b *Broker) Publish(topic string, m Message) error {
	panic("TODO: implement")
}

// Subscribe binds a new queue to topic and returns its delivery channel plus an
// idempotent unsubscribe function. The channel is owned by the broker and must be
// closed exactly once — by unsubscribe or by Close — never by the caller.
func (b *Broker) Subscribe(topic string, opts ...Option) (<-chan Delivery, func()) {
	panic("TODO: implement")
}

// DeadLettered returns a snapshot of every dead-lettered message across all live
// subscriptions.
func (b *Broker) DeadLettered() []Message {
	panic("TODO: implement")
}

// Close shuts the broker down: stops every queue, closes every delivery channel
// exactly once, and rejects further Publish/Subscribe with ErrClosed. Must be
// idempotent and leak no goroutines.
func (b *Broker) Close() error {
	panic("TODO: implement")
}
