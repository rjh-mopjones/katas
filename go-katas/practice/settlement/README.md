# Settlement Context Propagation

> A settlement service that pays out winning bets via a downstream dependency, threading the caller's context into every call so cancellation and timeouts stop the work promptly.

## The problem

When a market is settled, your service must pay out every winning bet by calling a
downstream payout dependency (in real life an HTTP/RPC call to a payments system),
once per bet. The caller of `Settle` owns a `context.Context` carrying a deadline
and/or a cancel signal — "give up after 2s", or "this settlement was abandoned,
stop now". Your job is to honour it: when the caller gives up, downstream work
must stop, with no orphaned calls firing payouts on a settlement nobody is waiting
for.

## Requirements

- `Settle` processes the bets **in order**, calling `payout(ctx, bet)` for each.
- Before each call, check `ctx.Err()`; if the context is cancelled or timed out,
  **stop immediately**, abandon the remaining bets, and return the (wrapped) ctx error.
- Pass the caller's `ctx` into every payout — **never** `context.Background()` — so
  cancellation/timeout propagates downstream.
- Return the **first error** encountered; the returned `settled` count reflects only
  the bets that completed successfully before the stop.
- A context already cancelled before the first iteration produces **zero** payout calls.
- No data races (`go test -race` clean). No `time.Sleep` for synchronization.

## What you implement

- `func NewSettler(payout PayoutFunc) *Settler`
- `func (s *Settler) Settle(ctx context.Context, bets []Bet) (settled int, err error)`
- `type PayoutFunc func(ctx context.Context, bet Bet) error` — the injected downstream call.

## The real challenge

This kata is about **context propagation**.

The seductive bug is calling the dependency with a fresh, detached context —
`payout(context.Background(), bet)` or `context.TODO()`. `context.Background()` has
no deadline and is never cancelled, so passing it **severs the link to the caller**:
the caller's timeout fires, the caller's cancel is invoked, your own `ctx` is
`Done` — and the payout call sails on regardless, because the context it holds knows
nothing about any of that. The consequences are concrete:

- **Wasted spend / load** — the dependency keeps doing real (billable) work for a
  settlement nobody awaits.
- **Payouts on an abandoned settlement** — money can move for a settlement the
  caller explicitly cancelled (a correctness/audit problem, not just performance).
- **Goroutine / connection leaks** — child work blocked on a detached call never
  observes cancellation and outlives the request that spawned it.

The rule: **pass ctx down, never swallow it.** The context you receive is the one
you propagate; you may *narrow* it but never replace it with a detached one.

- **Check `ctx.Err()` between iterations.** Guarding at the top of each loop, before
  dispatching the next call, gives prompt cancellation and bounds overrun to at most
  one in-flight call.
- **Propagation is a two-sided contract.** You *pass* `ctx` into `payout`; a
  well-behaved `payout` *observes* it (selects on `ctx.Done()`, attaches it to the
  outbound request, returns `ctx.Err()` promptly). You can't save a payout that
  ignores its ctx — but you can refuse to *start* further calls once ctx is dead.
- **Per-operation budget.** When one call deserves its own deadline, derive a child:
  `callCtx, cancel := context.WithTimeout(ctx, budget); defer cancel()`. `WithTimeout`
  preserves propagation (the child dies if the parent dies *or* the budget elapses).
  The `defer cancel()` is mandatory — the timer/child hold resources that leak until
  cancelled even on the success path (`go vet`'s lostcancel check will flag a miss).

## Run

No tests ship with this kata — designing the tests is part of the exercise. Write
your own under `practice/settlement/`, then:

```bash
cd go-katas/practice && go test -race ./settlement/
```

## Reference

Compare against the answer key in `go-katas/solution/settlement/`.

Extension: bounded-concurrency settlement — a worker pool that settles up to N bets
concurrently while STILL propagating ctx into every call and **cancelling its
siblings on the first failure** (the errgroup pattern, stdlib only:
`context.WithCancel` + a buffered channel as a semaphore + `sync.Once` to capture
the first error and `cancel()`).
