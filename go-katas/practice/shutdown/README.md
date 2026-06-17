# Graceful Shutdown

> A bet-processing server with a worker pool that shuts down cleanly on deploy: stop accepting, drain in-flight work, and exit without deadlocking, losing messages, or panicking.

## The problem

You run a bet-processing server. Callers `Submit` jobs onto a bounded internal
queue; a fixed pool of workers drains the queue and processes each job. On every
deploy the process receives a shutdown signal and must stop **gracefully**:

- stop accepting new jobs,
- finish (drain) everything already submitted — both in-flight and queued,
- and exit.

This is the WaitGroup / channel-close coordination kata. The happy path is
trivial; the entire difficulty is shutting down without breaking.

## Requirements

- Many goroutines may call `Submit` concurrently; a fixed pool of workers processes jobs.
- `Submit` on a shutting-down server returns `ErrShuttingDown` — it must **never** panic.
- `Shutdown` drains all queued and in-flight jobs before returning `nil` (no message loss).
- `Shutdown` respects its `context`: if the deadline fires before the drain completes,
  return the context error instead of hanging.
- `Shutdown` is idempotent — calling it twice must not panic.
- No data races (`go test -race` clean). No `time.Sleep` for synchronization.

## What you implement

- `func NewServer(workers, queueSize int, process func(Job)) *Server`
- `func (s *Server) Start()` — launch the worker pool.
- `func (s *Server) Submit(j Job) error`
- `func (s *Server) Shutdown(ctx context.Context) error`
- `var ErrShuttingDown = errors.New("shutdown: server is shutting down")`

## The real challenge

Three bugs lurk in the shutdown path:

1. **Send on a closed channel panics.** `Submit` sends on the jobs channel;
   `Shutdown` wants to close it. If they race, `Submit` panics. You cannot just
   "let the sender close it" — there are many senders. Fence `Submit` off first:
   signal shutdown by closing a separate `done` channel (exactly once, via
   `sync.Once`), have `Submit` check `done` and bail with `ErrShuttingDown`, and
   make the check-then-send atomic against the close (e.g. an `RWMutex`: `Submit`
   takes the read lock around the send; `Shutdown` takes the write lock before
   closing jobs). Close the jobs channel **exactly once**. "Only the sender closes,
   and exactly once."
2. **Lost in-flight messages.** Closing the jobs channel or returning before the
   workers drain the buffer loses queued work. Count the work with a
   `sync.WaitGroup`: register the workers, each worker calls `Done` only when its
   `range jobs` loop ends. Ranging over a closed channel yields all buffered jobs
   *before* ending — so closing jobs drains the queue, and `wg.Wait()` waits for it.
3. **Deadlock / hang.** A bare `wg.Wait()` hangs forever if a worker is wedged.
   Wrap it: run `Wait` in a goroutine that closes a `finished` channel, then
   `select` on `finished` versus `ctx.Done()`. If the context fires first, return
   `ctx.Err()` instead of hanging.

## Run

No tests ship with this kata — designing the tests is part of the exercise. Write
your own under `practice/shutdown/`, then:

```bash
cd go-katas/practice && go test -race ./shutdown/
```

## Reference

Compare against the answer key in `go-katas/solution/shutdown/`.

Extension: add a hard-deadline drain that, on `ctx` timeout, stops draining and
reports how many queued jobs were dropped (for observability); and add
backpressure to `Submit` — a `default:` arm on the send that returns an
`ErrQueueFull` when the queue is full instead of blocking, so callers shed load.
