// Package oddsfeed implements a worker-pool consumer for an incoming odds feed,
// designed so that it NEVER leaks goroutines and always shuts down cleanly.
//
// The scenario: a betting platform receives a stream of odds-feed messages over
// a channel. To keep up, a Consumer fans the stream out across a fixed pool of
// worker goroutines, each applying a Handler to every message. The interesting
// part is not the throughput — it is the lifecycle. A request (or the whole
// process) eventually gives up: the input stops and/or the caller cancels. Every
// worker MUST notice and exit. Nothing may keep running after the caller has
// moved on.
//
// # The goroutine-leak failure mode
//
// The naive worker loops on a bare receive:
//
//	for m := range in { h(ctx, m) }   // or: for { m := <-in; h(ctx, m) }
//
// If the producer never closes in (a feed that stalls, an upstream that holds
// the connection open, a request that is abandoned mid-flight), each worker
// blocks forever on the receive. A blocked goroutine is not free: it pins its
// stack, any variables it closed over, and — for a feed consumer — the live feed
// connection and buffers behind the handler. These goroutines survive the
// request that spawned them. Under load the process accumulates thousands of
// them: memory climbs, file descriptors and sockets are never released, and the
// leak is invisible to ordinary error handling because nothing ever errors — the
// goroutines are simply parked. This is the classic Go goroutine leak.
//
// # Why BOTH exit conditions are required
//
// A worker must select on two things at once:
//
//   - <-ctx.Done(): the caller cancelled (timeout, request aborted, shutdown).
//     Without this arm a worker blocked on <-in cannot be told to stop, so a
//     never-closing channel leaks the pool. This is the arm the naive loop omits.
//   - m, ok := <-in with an `ok` (channel-closed) check: the producer finished
//     and closed the channel. A closed channel yields the zero value forever with
//     ok == false; without the check a worker would spin on zero-value messages
//     instead of returning. This is the arm that `for range` gives you for free
//     but a hand-rolled `<-in` loop forgets.
//
// Only with both does every worker have a guaranteed exit on every shutdown path
// — cancellation OR end-of-stream — and therefore no leak.
//
// # Why Run joins with a WaitGroup (Run is synchronous)
//
// Run does not return until every worker goroutine has actually exited; it joins
// them via sync.WaitGroup. This makes Run a synchronous, fire-and-join boundary:
// when Run returns, the caller has a hard guarantee that the pool is gone, no
// handler is still in flight, and no goroutine is left holding resources. A Run
// that returned before its workers drained would itself be a leak — the caller
// would believe shutdown completed while goroutines kept running.
//
// # Detecting leaks in a test
//
// The companion test uses runtime.NumGoroutine: record the count before, run a
// full feed/cancel cycle, then poll until the count returns to the baseline. If a
// worker leaked, the count stays elevated and the assertion fails. Polling (a
// bounded retry with runtime.Gosched / a few millisecond sleeps) is acceptable
// here because it only waits for the scheduler to reap already-returning
// goroutines — it is not used to synchronise the logic under test.
//
// # Extension: bounded in-flight work + graceful drain
//
// Two natural extensions, left for the practice version:
//
//   - Bounded in-flight: cap concurrent handler invocations with a semaphore
//     channel (a buffered chan struct{} of capacity N) acquired before h and
//     released after, so a slow handler cannot let unbounded work pile up.
//   - Graceful drain: on shutdown, stop accepting new messages but let in-flight
//     handlers finish (e.g. distinguish "cancel now" from "close input and drain")
//     rather than abandoning partially processed messages.
package oddsfeed

import (
	"context"
	"sync"
)

// Message is a single odds-feed update: the market it concerns and its payload.
type Message struct {
	Market  string
	Payload string
}

// Handler performs the per-message work. It receives the Consumer's context so a
// long-running handler can itself observe cancellation.
type Handler func(context.Context, Message)

// Consumer fans an incoming Message stream out across a fixed pool of workers,
// applying its Handler to every message. The zero value is not usable; construct
// one with NewConsumer.
type Consumer struct {
	workers int
	handler Handler
}

// NewConsumer returns a Consumer that will run `workers` worker goroutines, each
// applying h to received messages. workers should be >= 1.
func NewConsumer(workers int, h Handler) *Consumer {
	return &Consumer{workers: workers, handler: h}
}

// Run starts the worker pool reading from in and blocks until every worker has
// exited.
//
// It fans out c.workers goroutines. Each worker loops, selecting on both
// ctx.Done() (caller cancelled) and a receive from in with a channel-closed
// check (producer finished). A worker returns on either condition, so neither a
// never-closing channel nor an abandoned context can leave it parked — there is
// no goroutine leak.
//
// Run joins the pool with a sync.WaitGroup, so it returns only once the pool is
// fully drained: when it returns, no worker goroutine and no handler invocation
// is still running. Run reports ctx.Err() if the context was cancelled, and nil
// if the workers stopped because in was closed.
func (c *Consumer) Run(ctx context.Context, in <-chan Message) error {
	var wg sync.WaitGroup
	wg.Add(c.workers)
	for i := 0; i < c.workers; i++ {
		go func() {
			defer wg.Done()
			for {
				select {
				case <-ctx.Done():
					return
				case m, ok := <-in:
					if !ok {
						return
					}
					c.handler(ctx, m)
				}
			}
		}()
	}
	wg.Wait()
	return ctx.Err()
}
