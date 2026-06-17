# Odds Feed Consumer

> A worker-pool consumer for an incoming odds feed that shuts down cleanly and never leaks a goroutine.

## The problem

A betting platform receives a stream of odds-feed messages over a channel. To
keep up, you fan the stream out across a fixed pool of worker goroutines, each
applying a handler to every message. The hard part is not throughput — it is the
lifecycle. Eventually the caller gives up: the input stops, or the context is
cancelled (timeout, shutdown, abandoned request). Every worker must notice and
exit. Nothing may keep running after the caller has moved on.

## Requirements

- Fan a `<-chan Message` out across a fixed number of workers, each applying the
  handler to every received message.
- Every worker must exit when **either** the context is cancelled **or** the
  input channel is closed.
- `Run` must block until all workers have actually exited, then return
  `ctx.Err()` if cancelled, otherwise `nil`.
- No goroutine may survive `Run` returning. Must be `-race` clean.
- Standard library only.

## What you implement

- `NewConsumer(workers int, h Handler) *Consumer` — build a consumer with a fixed
  pool size and a per-message handler.
- `Run(ctx context.Context, in <-chan Message) error` — fan out the workers,
  process messages, and **block until every worker has exited** before returning.
  Return `ctx.Err()` on cancellation, `nil` on a closed input.

## The real challenge

This kata is about goroutine leaks and clean shutdown.

- **The leak.** The naive worker loops on a bare receive (`for m := range in` or
  `for { m := <-in; ... }`). If the producer never closes `in` — a stalled feed,
  an upstream holding the connection open, an abandoned request — each worker
  blocks forever on the receive. A parked goroutine is not free: it pins its
  stack, everything it closed over, and the live feed connection/buffers behind
  the handler. These goroutines outlive the request that spawned them; under load
  the process accumulates thousands, memory and sockets are never released, and
  nothing ever errors — they are simply parked. That is the classic Go goroutine
  leak.
- **Both exit conditions are required.** A worker must `select` on two things at
  once: `<-ctx.Done()` (so a never-closing channel can still be told to stop) and
  `m, ok := <-in` with the channel-closed `ok` check (so a finished, closed
  channel makes the worker return instead of spinning on zero values). `for range`
  gives you the second for free but the first never; a hand-rolled `<-in` loop
  tends to forget the second. You need both.
- **WaitGroup join makes `Run` synchronous.** Join the pool with a
  `sync.WaitGroup` so `Run` returns only once every worker has exited. Then the
  caller has a hard guarantee that the pool is gone and no handler is still in
  flight. A `Run` that returned early would itself be a leak.
- **Detecting leaks.** Record `runtime.NumGoroutine()` before, run a full
  feed/cancel cycle, then poll until the count returns to the baseline. A bounded
  retry loop (`runtime.Gosched()` / a few `time.Sleep(time.Millisecond)`) just
  gives the scheduler time to reap already-returning goroutines — it is fine, it
  is not synchronising the logic under test.

## Run

There are no tests here — designing them is part of the exercise. Write your own
under this package, then:

```
cd go-katas/practice && go test -race ./oddsfeed/
```

## Reference

A full, leak-free, `-race`-clean implementation with interview-grade doc comments
lives in `go-katas/solution/oddsfeed/`.

Extension: bound in-flight work with a semaphore channel (a buffered
`chan struct{}` acquired before the handler and released after) so a slow handler
cannot let unbounded work pile up, and add a **graceful drain** on shutdown —
stop accepting new messages but let in-flight handlers finish, rather than
abandoning partially processed messages.
