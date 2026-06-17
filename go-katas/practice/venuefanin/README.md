# Venue Fan-In

> A fan-in aggregator that merges price quotes from many venue feeds onto one downstream channel — with backpressure, no drops, no duplicates, and clean shutdown.

## The problem

A pricing service subscribes to several venue feeds (NYSE, LSE, TSE, …), each
delivered as its own `<-chan Quote`. Downstream wants a single stream. You merge
the feeds with a fan-in: one channel out, every quote from every venue on it.

The hard parts are all at once. Merge **without dropping or duplicating** any
quote. Keep memory **bounded** so a fast venue can't grow the process without
limit while the consumer lags (backpressure). And **terminate cleanly** — close
the output when every source has drained *or* the context is cancelled — without
leaking a goroutine and without panicking on a double close.

## Requirements

- Merge N source channels onto one output channel: every quote from every source
  appears on the output **exactly once** — no drop, no duplicate.
- The output channel is **buffered to `bufferSize`** and that bound is the only
  in-flight buffering: backpressure, not unbounded spooling.
- The output closes when **either** all sources are closed/drained **or** the
  context is cancelled.
- No goroutine may survive a cancelled/drained `FanIn`. Must be `-race` clean.
- Standard library only.

## What you implement

- `FanIn(ctx context.Context, bufferSize int, sources ...<-chan Quote) <-chan Quote`
  — start one forwarder per source, merge onto a bounded output channel, and
  return the receive-only output for the consumer to range over.

## The real challenge

This kata is about the fan-in pattern, backpressure, and clean termination.

- **No drop, no dup.** Each source gets one forwarding goroutine that receives
  every quote once and forwards it once. Receiving twice or forwarding to two
  outputs duplicates; closing the output before a forwarder has sent its last
  value drops.
- **WaitGroup-then-close.** The output must be closed **exactly once** and **only
  after every forwarder has finished**. Close it too early and an in-flight
  forwarder hits a send on a closed channel (panic) or its quote is lost; close it
  from inside more than one forwarder and the second `close` panics. The idiom:
  every forwarder `wg.Done()`s on exit; one dedicated closer goroutine does
  `wg.Wait()` then `close(out)` — single owner, exactly once, after the last send.
- **Bounded buffer = backpressure (the bug).** A fixed-capacity output channel
  makes forwarders block when the consumer lags, which propagates back and makes
  the fast venues wait — memory stays bounded by `bufferSize`. The wrong "fix" is
  spooling overflow into an unbounded slice/queue so sends never block: a fast
  venue then outpaces the consumer forever and the backing store grows until OOM.
  Unbounded buffering doesn't remove backpressure, it just hides it until you run
  out of RAM.
- **Select on `ctx.Done()` on the send, not just the receive.** If the consumer
  goes away while the output buffer is full, a bare `out <- q` parks the forwarder
  forever — and `wg.Wait()` never completes, so the output never closes either.
  That is a goroutine leak. Each send must race `ctx.Done()` so the forwarder can
  bail when the consumer is gone.
- **Backpressure vs load-shed.** Blocking (backpressure) guarantees completeness
  but couples every venue to the slowest consumer. Load-shed (a `default:` arm
  that drops on a full buffer) favours liveness at the cost of data loss. For
  quotes, blind dropping is usually wrong — you may drop the newest tick and serve
  a stale one — so the better shed is latest-wins **coalescing** per market.

## Run

There are no tests here — designing them is part of the exercise. Write your own
under this package, then:

```
cd go-katas/practice && go test -race ./venuefanin/
```

## Reference

A full, leak-free, `-race`-clean implementation with interview-grade doc comments
lives in `go-katas/solution/venuefanin/`.

Extension: **latest-wins per-market coalescing under backpressure** — instead of
blocking or blind-dropping when the buffer is full, keep only the most recent
quote per `Market` and overwrite older un-sent ones, so under pressure you shed
intermediate ticks but never deliver a stale price.
