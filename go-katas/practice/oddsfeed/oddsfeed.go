package oddsfeed

import "context"

// Message is a single odds-feed update: the market it concerns and its payload.
type Message struct {
	Market  string
	Payload string
}

// Handler performs the per-message work. It receives the Consumer's context so a
// long-running handler can itself observe cancellation.
type Handler func(context.Context, Message)

type Consumer struct{}

func NewConsumer(workers int, h Handler) *Consumer {
	panic("TODO: implement")
}

func (c *Consumer) Run(ctx context.Context, in <-chan Message) error {
	panic("TODO: implement")
}
