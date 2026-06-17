# Feed Channel Broker

> A price-feed fan-out broker built on channels: publishers push price updates, one subscriber consumes them, and shutdown is clean.

## The problem

You are building the broker at the heart of a market-data service. Many publisher
goroutines push price `Update`s; a single subscriber consumes them by ranging over
a receive channel. The broker must apply bounded backpressure when the subscriber
falls behind, and shut down cleanly without ever panicking or hanging.

This is the canonical Go channel-discipline exercise. The whole difficulty is in
the edges, not the happy path.

## Requirements

- Many goroutines may call `Publish` concurrently; one goroutine consumes `Updates()`.
- A bounded buffer absorbs bursts; once full, a publisher experiences backpressure.
- `Publish` on a closed broker returns `ErrClosed` — it must **never** panic.
- `Publish` blocked on a full buffer must return the context error if the caller's
  context is cancelled or times out (backpressure surfaces as `ctx.Err()`, not data loss).
- `Close` is idempotent and terminates the subscriber's `range` over `Updates()`.
- No data races (`go test -race` clean). No `time.Sleep` for synchronization.

## What you implement

- `func NewBroker(buffer int) *Broker` — bounded backpressure window.
- `func (b *Broker) Publish(ctx context.Context, u Update) error`
- `func (b *Broker) Updates() <-chan Update`
- `func (b *Broker) Close()`
- `var ErrClosed = errors.New("feedchannel: broker closed")`

## The real challenge

Three classic channel bugs, and the idioms that kill them:

1. **Send on a closed channel panics.** `close(ch)` is irreversible; a later
   `ch <- v` panics. Don't let publishers close anything or send blindly. Signal
   shutdown by closing a separate `done` channel exactly once (guard it with
   `sync.Once`), and have `Publish` `select` on `done` so it returns `ErrClosed`
   instead of racing the send. "Only the sender closes, and only once."
2. **A nil channel blocks forever.** The zero-value `Broker` has nil channels —
   reads/writes hang with no error. Force construction via `NewBroker`. (The same
   property is useful: setting a channel to nil inside a `select` disables that case.)
3. **A full/unbuffered channel applies backpressure.** A naive `ch <- v` blocks
   indefinitely if the subscriber stalls. Use a bounded buffer for bursts, and wrap
   the send in a `select` on `ctx.Done()` so a blocked publisher surfaces the
   caller's timeout/cancel instead of hanging.

**Blocking vs dropping:** this broker blocks under backpressure (bounded by ctx).
The alternative is a `default:` arm that drops the update when the buffer is full —
favouring publisher latency and bounded memory at the cost of data loss. Think
about which fits a price feed where only the latest tick matters.

## Run

No tests ship with this kata — designing the tests is part of the exercise. Write
your own under `practice/feedchannel/`, then:

```bash
cd go-katas/practice && go test -race ./feedchannel/
```

## Reference

Compare against the answer key in `go-katas/solution/feedchannel/`.

Extension: turn this into a true fan-out broker — multiple subscribers, each with
its own per-subscriber slow-consumer policy (drop vs block), so one slow consumer
can't stall the others.
