package messagebus

import "errors"

// Sentinel errors returned across the broker API.
var (
	// ErrClosed is returned by Publish/Subscribe once Close has begun.
	ErrClosed = errors.New("messagebus: broker is closed")
	// ErrQueueFull is returned by Publish when a destination queue is at capacity.
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
// acknowledges. A consumer MUST eventually call exactly one of Ack or Nack on each
// Delivery; both must be idempotent.
type Delivery struct {
	// Message is the payload being delivered.
	Message Message
	// Redelivered is this message's delivery count: 1 on first delivery, 2 on the
	// first redelivery, and so on.
	Redelivered int

	// (Implementation note: you will need some private wiring here to route Ack/Nack
	// back to the owning queue and to make them idempotent.)
}

// Ack acknowledges successful processing: the broker drops the message and never
// redelivers it. Must be idempotent.
func (d *Delivery) Ack() {
	panic("TODO: implement")
}

// Nack negatively acknowledges the delivery. If requeue is true the message is
// returned for redelivery (subject to the maxRetries cap, after which it is
// dead-lettered); if false it is dead-lettered immediately. Must be idempotent.
func (d *Delivery) Nack(requeue bool) {
	panic("TODO: implement")
}
