// Package messagebus is an in-memory message broker that models the delivery
// semantics of RabbitMQ — topic routing, bounded queues, manual ack/nack with
// redelivery, per-consumer prefetch (QoS), and dead-lettering of poison messages
// — using nothing but goroutines, channels, and structs. There is no real broker:
// the point is to internalise the *semantics* a production AMQP broker gives you,
// and the obligations they place on your code, by building a faithful model.
//
// # The mental model: exchange / binding / queue
//
// In RabbitMQ a publisher never talks to a queue directly. It publishes to an
// *exchange* with a *routing key*; the exchange matches that key against the
// *bindings* of the queues attached to it and copies the message into every queue
// whose binding matches. A *consumer* then subscribes to a queue and receives
// deliveries. This broker collapses exchange+binding into Broker.Publish (the
// topic is the routing key) and gives each Subscribe its own queue, but the shape
// is the same: Publish routes by topic to zero or more queues; each queue feeds
// one subscriber. Topic matching supports an exact match and a single-level
// trailing wildcard `prefix.*` (see Broker.Publish for the exact rule), the
// analogue of AMQP topic-exchange bindings.
//
// # At-least-once delivery, and why your consumer MUST be idempotent
//
// The headline semantic is *at-least-once* delivery, which falls directly out of
// manual acknowledgement. A delivery is not considered done when it leaves the
// queue; it is done only when the consumer calls Ack. Until then it is "unacked"
// and the broker still owns it. If the consumer fails to ack — it crashes, it
// nacks-with-requeue, or its ack times out — the broker redelivers the message.
// That guarantees no message is lost (at-least-once) but explicitly permits the
// same message to be delivered more than once.
//
// The real-money consequence: a consumer that "settles a bet" or "pays a winner"
// in response to a message MUST be idempotent, keyed on Message.ID. A redelivered
// "settle bet b-42" must not pay twice. The standard pattern is a processed-set:
//
//	if seen.Contains(d.Message.ID) { d.Ack(); return } // already done, just ack
//	doTheWork(d.Message)
//	seen.Add(d.Message.ID)
//	d.Ack()
//
// The alternative, *exactly-once* delivery, does not exist over an unreliable
// channel without a distributed transaction spanning the broker and the
// consumer's side effect — which is why mainstream brokers (RabbitMQ, SQS, Kafka
// consumers) offer at-least-once and push idempotency onto you. Modelling that
// honestly — redelivery is a feature, dedupe is your job — is the lesson.
//
// # Prefetch / QoS: bounding in-flight work as backpressure
//
// Without a limit, a queue would shove every message at a consumer the instant it
// connects, regardless of whether the consumer can keep up — unbounded in-flight
// work and memory. RabbitMQ's basic.qos prefetch count caps the number of unacked
// deliveries outstanding to a consumer at once. This broker does the same:
// WithPrefetch(n) means at most n deliveries are in flight (delivered but not yet
// acked) before the queue stops dispatching. Each Ack/Nack frees a slot and lets
// the next message flow. Prefetch is the broker's backpressure knob: it paces the
// producer side to the consumer's actual throughput and is what makes a slow
// consumer safe rather than a memory leak.
//
// # Poison messages and the dead-letter queue
//
// A message that fails every time — malformed, references a deleted entity,
// triggers a bug — would, under naive redelivery, loop forever, blocking the
// queue and burning CPU. The fix is a redelivery cap: track a per-message
// delivery count and, once it exceeds WithMaxRetries, stop redelivering and route
// the message to a *dead-letter queue* (DLQ) instead. The DLQ is a parking lot
// for human inspection — the analogue of a RabbitMQ dead-letter exchange (DLX).
// DeadLettered() exposes it. This bounds the blast radius of a poison message to
// maxRetries+1 attempts.
//
// # Channel ownership and clean shutdown
//
// Each Subscribe returns a receive-only delivery channel that the broker owns and
// must close *exactly once*, on Close or Unsubscribe — never on the consumer side
// — because the close-once / only-the-owner-closes rule is what prevents both a
// send-on-closed-channel panic and a double close. Close is idempotent (guarded by
// sync.Once), stops every per-queue goroutine, closes every delivery channel
// exactly once, and leaves no goroutine leaked. Publish and Subscribe racing a
// concurrent Close must error cleanly (ErrClosed), never panic.
package messagebus

import "errors"

// Sentinel errors returned across the broker API.
var (
	// ErrClosed is returned by Publish/Subscribe once Close has begun.
	ErrClosed = errors.New("messagebus: broker is closed")
	// ErrQueueFull is returned by Publish when a destination queue is at capacity.
	// The broker uses a non-blocking publish policy: a full queue sheds the message
	// rather than blocking the publisher (see Broker.Publish for the rationale).
	ErrQueueFull = errors.New("messagebus: queue is full")
)

// Message is an immutable unit published to the broker.
//
// ID is the idempotency key: at-least-once delivery means the same Message can be
// delivered more than once, so consumers performing side effects must dedupe on
// ID. Topic is the routing key matched against subscriptions. Body is the opaque
// payload.
type Message struct {
	ID    string
	Topic string
	Body  []byte
}

// Delivery is one attempt to hand a Message to a consumer — the unit a consumer
// acknowledges. The same Message may appear in several Deliveries over its life
// (each redelivery is a new Delivery with an incremented Redelivered count).
//
// A consumer MUST eventually call exactly one of Ack or Nack on each Delivery.
// Both are idempotent on the Delivery: a second call is a no-op, so an
// accidental double-ack cannot corrupt the queue's accounting.
type Delivery struct {
	// Message is the payload being delivered.
	Message Message
	// Redelivered is this message's delivery count: 1 on first delivery, 2 on the
	// first redelivery, and so on. A value > 1 is the consumer's signal that it may
	// be seeing a duplicate and should consult its dedupe set.
	Redelivered int

	// queue is the owning queue, used to route Ack/Nack back. ackOnce makes
	// Ack/Nack idempotent for this Delivery.
	queue   *queue
	ackOnce func(ack bool, requeue bool)
}

// Ack acknowledges successful processing: the broker drops the message and never
// redelivers it. Idempotent — a second Ack (or an Ack after Nack) is a no-op.
func (d *Delivery) Ack() {
	if d.ackOnce != nil {
		d.ackOnce(true, false)
	}
}

// Nack negatively acknowledges the delivery. If requeue is true the message is
// returned to the queue for redelivery (subject to the maxRetries cap, after
// which it is dead-lettered); if false it is dead-lettered immediately. Idempotent
// — a second Nack (or a Nack after Ack) is a no-op.
func (d *Delivery) Nack(requeue bool) {
	if d.ackOnce != nil {
		d.ackOnce(false, requeue)
	}
}
