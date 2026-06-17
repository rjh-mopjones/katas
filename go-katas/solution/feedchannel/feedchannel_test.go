package feedchannel

import (
	"context"
	"errors"
	"testing"
)

func TestPublish_DeliversInOrder(t *testing.T) {
	b := NewBroker(8)
	want := []Update{
		{Market: "AAPL", Price: 100.5},
		{Market: "GOOG", Price: 200.25},
		{Market: "MSFT", Price: 300.75},
	}

	for _, u := range want {
		if err := b.Publish(context.Background(), u); err != nil {
			t.Fatalf("Publish(%v) returned error: %v", u, err)
		}
	}

	for i, w := range want {
		got := <-b.Updates()
		if got != w {
			t.Fatalf("update %d: got %v, want %v", i, got, w)
		}
	}
}

func TestPublish_AfterClose_ReturnsErrClosed(t *testing.T) {
	b := NewBroker(4)
	b.Close()

	err := b.Publish(context.Background(), Update{Market: "AAPL", Price: 1})
	if !errors.Is(err, ErrClosed) {
		t.Fatalf("Publish after Close: got err %v, want ErrClosed", err)
	}
}

func TestClose_Idempotent(t *testing.T) {
	b := NewBroker(2)
	b.Close()
	b.Close() // must not panic on double close.
}

func TestPublish_BlocksThenCancelled(t *testing.T) {
	b := NewBroker(1)

	// Fill the single buffer slot so the next send must block.
	if err := b.Publish(context.Background(), Update{Market: "AAPL", Price: 1}); err != nil {
		t.Fatalf("first Publish returned error: %v", err)
	}

	// With the buffer full and no receiver, this Publish can only block on the
	// send — so the cancelled context must win the select and surface as ctx.Err.
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	err := b.Publish(ctx, Update{Market: "AAPL", Price: 2})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("Publish under backpressure with cancelled ctx: got %v, want context.Canceled", err)
	}

	// The dropped update must not have displaced the buffered one (no data loss).
	got := <-b.Updates()
	if want := (Update{Market: "AAPL", Price: 1}); got != want {
		t.Fatalf("buffered update: got %v, want %v", got, want)
	}
}

func TestClose_TerminatesRange(t *testing.T) {
	b := NewBroker(4)

	b.Publish(context.Background(), Update{Market: "AAPL", Price: 1})
	b.Publish(context.Background(), Update{Market: "GOOG", Price: 2})

	done := make(chan int)
	go func() {
		count := 0
		for range b.Updates() {
			count++
		}
		done <- count
	}()

	b.Close()

	count := <-done // blocks until the range loop exits, proving Close terminates it.
	if count < 0 || count > 2 {
		t.Fatalf("subscriber received %d updates, want 0..2", count)
	}
}
