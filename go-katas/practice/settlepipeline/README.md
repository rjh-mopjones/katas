# Settlement Pipeline

> A staged, bounded-concurrency settlement pipeline: validate → reserve → settle → notify, with end-to-end context cancellation, first-error short-circuit, and a clean, leak-free drain.

## The problem

When a market settles, every bet on it must flow through four stages —
**validate → reserve funds → settle → notify**. Each stage runs a bounded
worker pool, and the stages are connected by bounded channels so a fast stage
can't run away from a slow one (backpressure). Build the pipeline so that:

- it processes every bet end-to-end and reports the settled IDs;
- the caller's **context cancellation propagates end-to-end** — when the caller
  gives up, every in-flight stage stops promptly and no work continues;
- a fatal error from any stage **short-circuits** the whole pipeline (cancel
  siblings, stop feeding) and is returned;
- on normal completion the pipeline **drains gracefully** — no lost items, every
  channel closed exactly once in order, and no leaked goroutine.

## Requirements

- One reusable generic `Stage` is the building block; the four concrete stages
  are just `Stage` calls chained output→input.
- Each stage has a **bounded worker pool** and a **bounded output buffer**
  (backpressure between stages).
- `Run` returns the notified bet IDs on success, or the first fatal error.
- Cancellation (caller ctx) and the first error both tear the pipeline down
  promptly; `Run` returns the context error or the stage error respectively.
- No goroutine may survive a returned `Run` — success, cancel, or error paths.
  Must be `-race` clean.
- Standard library only.

## What you implement

- `Stage[I, O any](ctx context.Context, in <-chan I, workers, buffer int, fn func(context.Context, I) (O, error)) (<-chan O, <-chan error)`
  — a reusable bounded-concurrency stage: `workers` goroutines consume `in`,
  apply `fn`, send results to a bounded `out` (cap `buffer`) and errors to
  `errc`; close both exactly once after all workers finish.
- `Pipeline` + `New(fns StageFuncs, workers, buffers [4]int) *Pipeline` + its
  `Run(ctx context.Context, bets []Bet) ([]string, error)` method — wire the four
  stages with `Stage`, feed the bets respecting ctx, fan the final stage into a
  result slice, and short-circuit on the first error via a derived cancel.

## Stages

A ~60-minute path; get each layer working before adding the next.

1. **Sequential stages wired over channels.** Implement `Stage` and chain four
   of them (validate → reserve → settle → notify) with `workers = buffer = 1`.
   A feeder goroutine pushes the bets onto the head channel; the final stage's
   output is collected into the result slice. Prove the happy path with N bets.
2. **Bounded worker pool per stage.** Make `Stage` spawn `workers` goroutines
   over the same `in`, all feeding one shared `out` (WaitGroup-then-close).
   Verify observed max concurrency never exceeds the configured worker count.
3. **End-to-end context cancellation propagation.** Use one derived context for
   the feeder and all stages; select on `ctx.Done()` on every receive AND every
   send. A cancelled caller ctx must stop all in-flight work promptly.
4. **First-error short-circuit + graceful drain, no leaks.** Multiplex the
   stages' error channels; on the first error, cancel the derived context, drain
   to let workers exit, and return the error. On success, drain the notify output
   to completion and return the IDs. Either way, no goroutine survives.

## The real challenge

- **WaitGroup-then-close, once per stage.** A stage's output must be closed
  **exactly once** and **only after every worker has finished**. Close it early
  and an in-flight worker hits a send on a closed channel (panic) or its result
  is lost; close it from inside more than one worker and the second `close`
  panics. The idiom: each worker `wg.Done()`s on exit; one closer goroutine does
  `wg.Wait()` then `close(out)` (and `close(errc)`) — single owner, exactly once.
- **Bounded buffers = backpressure vs unbounded memory growth.** A fixed-capacity
  inter-stage channel makes a fast stage block when the next one lags, propagating
  the slowness upstream and keeping memory bounded. Replacing the bound with an
  unbounded slice/queue so sends never block just lets the fast stage balloon
  memory ahead of the slow one until OOM. Keep the bound.
- **Context must reach every in-flight stage, or work continues after the caller
  gave up.** Select on `ctx.Done()` on the **send** too, not just the receive —
  if a downstream consumer has stopped (cancel or a sibling's error) while a
  buffer is full, a bare send parks the worker forever, the closer's `wg.Wait()`
  never completes, and the goroutine leaks.
- **First-error short-circuit via a derived cancel.** Derive a cancellable
  context once and share it across the feeder and all stages. The clean way to
  stop the pipeline on the first error is to `cancel()` that context — every
  stage already watches it — rather than trying to signal each stage by hand.
- **Drain vs abandon.** On success you **drain**: let every stage finish so
  nothing is lost. On error/cancel you **abandon**: cancel, then drain the
  remaining channels only enough to unblock the workers so their closers run —
  completeness traded for a prompt, leak-free shutdown.

## Run

There are no tests here — designing them is part of the exercise. Write your own
under this package, then:

```
cd go-katas/practice && go test -race ./settlepipeline/
```

## Reference

A full, leak-free, `-race`-clean implementation with interview-grade doc comments
lives in `go-katas/solution/settlepipeline/`.

Extension: **per-stage metrics + dynamic worker-count tuning** — record per-stage
throughput/latency and in-flight counts, then justify each stage's worker count
with a benchmark or pprof profile (validate is cheap and CPU-bound; reserve and
notify are IO-bound, so their ideal pool sizes differ). Or wrap a flaky stage's
`fn` in a **retry-with-backoff** that still respects `ctx.Done()`.
