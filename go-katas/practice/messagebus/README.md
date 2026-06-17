# Message Bus (RabbitMQ-style)

> An in-memory message broker that models RabbitMQ delivery semantics — topic
> routing, bounded queues, manual ack/nack with redelivery, per-consumer prefetch
> (QoS), and dead-lettering of poison messages — built from goroutines, channels,
> and structs.

## The problem

You are building the messaging backbone of a trading platform. Real RabbitMQ is
overkill for the kata, so you model it in memory: publishers `Publish` messages to
topics; consumers `Subscribe` to topics and receive **deliveries**; consumers
**ack** a delivery when they have processed it, or **nack** it to redeliver or
reject. The broker must give the same guarantees a production AMQP broker gives —
and impose the same obligations on the consumer that those guarantees imply.

The headline guarantee is **at-least-once delivery**: a message is not "done" when
it leaves the queue, only when the consumer acks it. If the consumer never acks
(it crashes, nacks-with-requeue, or its ack times out), the broker redelivers.
That means the same message can arrive more than once — which, for a consumer that
moves money, is a correctness problem you must design around.

## Requirements

- **Topic routing.** `Publish` copies a message into every subscription whose bound
  topic matches. Support exact match and a single-level trailing wildcard `pre.*`
  (matches one more dot-segment: `markets.*` matches `markets.football`, not
  `markets` nor `markets.football.fulltime`). Publishing to an unlistened topic is
  not an error.
- **Manual ack/nack + redelivery.** A delivery is unacked until `Ack`. `Nack(true)`
  redelivers; `Nack(false)` rejects (dead-letters). Track a per-message delivery
  count and expose it as `Delivery.Redelivered` (1 on first delivery, incrementing
  on each redelivery). `Ack`/`Nack` are **idempotent** per delivery.
- **Prefetch / QoS.** At most `prefetch` unacked deliveries may be outstanding to a
  consumer at once; the queue stops dispatching until an ack/nack frees a slot.
- **Dead-lettering.** A message redelivered past `maxRetries` is routed to a
  dead-letter queue and stops being delivered. `DeadLettered()` exposes it.
- **Ack-timeout redelivery.** With `WithAckTimeout(d)`, an unacked delivery older
  than `d` (measured against the **injected clock**) is treated as
  nack-with-requeue. No real `time.Sleep` for this — drive it off `WithClock`.
- **Bounded queues.** `Publish` to a full queue returns `ErrQueueFull` (shed-on-full).
- **Clean shutdown.** `Close` is idempotent, stops everything, closes every delivery
  channel **exactly once** (no send-on-closed, no double close), and leaks no
  goroutines. Concurrent `Publish`/`Subscribe` racing `Close` return `ErrClosed` /
  a closed channel — never a panic. `go test -race` clean.

## What you implement

- `func New(opts ...Option) *Broker`
- `func (b *Broker) Publish(topic string, m Message) error`
- `func (b *Broker) Subscribe(topic string, opts ...Option) (<-chan Delivery, func())`
- `func (b *Broker) DeadLettered() []Message`
- `func (b *Broker) Close() error`
- `func (d *Delivery) Ack()` and `func (d *Delivery) Nack(requeue bool)`
- Options: `WithPrefetch(n)`, `WithMaxRetries(n)`, `WithQueueSize(n)`,
  `WithAckTimeout(d)`, `WithClock(func() time.Time)`.

The `Message`, `Delivery`, `Option`, and the error vars are given; design every
private type and field yourself.

## Stages

A ~60-minute path, each stage building on the last:

1. **Publish/subscribe + topic routing.** Get messages flowing: `Subscribe` returns
   a delivery channel, `Publish` routes by topic (exact match first, then the
   `pre.*` wildcard) to every matching queue. Prove a non-matching topic is not
   delivered. (Ignore acks for now — deliver everything.)
2. **Manual ack/nack + redelivery.** A delivery is unacked until `Ack`. Add
   `Nack(true)` → redeliver (increment `Redelivered`), `Nack(false)` → reject. Make
   `Ack`/`Nack` idempotent. This is what turns the bus into *at-least-once*.
3. **Prefetch / QoS = backpressure.** Cap unacked-in-flight per consumer at
   `prefetch`. Hold back the `N+1`th delivery until an ack/nack frees a slot. This
   is the backpressure that makes a slow consumer safe instead of a memory leak.
4. **Dead-letter + idempotent consumer.** Cap redeliveries at `maxRetries`; route a
   poison message to the DLQ once exceeded. Then add the ack-timeout (fake clock).
   Finally, write the consumer-side **dedupe on `Message.ID`** that at-least-once
   *forces*: a redelivered "settle bet" must pay exactly once.

## The real challenge

- **At-least-once → idempotent consumers (the money angle).** Redelivery is a
  *feature*: it is how the bus guarantees no message is lost. But it means a "settle
  bet b-42" message can arrive twice, so a consumer that pays out **must dedupe on
  `Message.ID`** (a processed-set: if seen, just ack; else do the work, record, ack)
  or it will pay a winner twice. *Exactly-once* delivery does not exist over an
  unreliable channel without a distributed transaction spanning broker and side
  effect — which is why real brokers offer at-least-once and push idempotency onto
  you. Model that honestly.
- **Prefetch as backpressure.** Without a cap, a queue floods a consumer with
  everything at once — unbounded in-flight work and memory. The prefetch count paces
  the producer to the consumer's real throughput; each ack frees one slot.
- **Poison messages → DLQ.** A message that fails every time would loop forever under
  naive redelivery. The retry cap bounds the blast radius to `maxRetries+1` attempts,
  then parks it in the dead-letter queue for human inspection.
- **Close-once channel ownership.** The broker *owns* every delivery channel and must
  close each exactly once (`sync.Once`), from the owning goroutine only — never the
  consumer. That is what prevents both a send-on-closed-channel panic and a double
  close during a `Close` that races live publishers/subscribers.
- **This is a MODEL.** You are reconstructing the semantics of an AMQP topic
  *exchange* + *binding* + *queue*, `basic.qos` (prefetch), `basic.nack`
  (requeue/reject), and a dead-letter exchange (DLX) — in memory, so the *semantics*
  are what you keep.

## Run

No tests ship with this kata — designing the tests is part of the exercise. Write
your own under `practice/messagebus/`, then:

```bash
cd go-katas/practice && go test -race ./messagebus/
```

## Reference

Compare against the answer key in `go-katas/solution/messagebus/`.

Extension: **competing consumers / consumer groups** — let multiple consumers share
one queue with fair round-robin dispatch (work-sharing, the classic RabbitMQ
worker-pool pattern), so adding consumers adds throughput. **Or** multi-level topic
wildcards like `a.#` (matches zero-or-more trailing segments) alongside the
single-level `a.*`.
