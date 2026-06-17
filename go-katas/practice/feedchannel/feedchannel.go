package feedchannel

import (
	"context"
	"errors"
)

// ErrClosed is returned by Publish when the broker has been closed.
var ErrClosed = errors.New("feedchannel: broker closed")

// Update is an immutable price tick for a single market.
type Update struct {
	Market string
	Price  float64
}

// Broker is a single-subscriber, fan-in price-feed broker.
type Broker struct{}

// NewBroker returns a ready Broker whose updates channel has the given buffer capacity.
func NewBroker(buffer int) *Broker {
	panic("TODO: implement")
}

// Publish sends u to the subscriber, blocking under backpressure until space is
// available, the context is done, or the broker is closed.
func (b *Broker) Publish(ctx context.Context, u Update) error {
	panic("TODO: implement")
}

// Updates returns the receive-only side of the feed for the subscriber to range over.
func (b *Broker) Updates() <-chan Update {
	panic("TODO: implement")
}

// Close shuts the broker down. It is idempotent.
func (b *Broker) Close() {
	panic("TODO: implement")
}
