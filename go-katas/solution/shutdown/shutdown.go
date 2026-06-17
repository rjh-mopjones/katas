// Package shutdown implements a bet-processing server with a worker pool and a
// graceful shutdown protocol. It is the WaitGroup / channel-close coordination
// kata: the hard part is not processing jobs, it is stopping cleanly on deploy.
//
// A Server accepts Jobs onto a bounded internal channel, which a fixed pool of
// workers drains by ranging over it. On deploy the process must shut down
// gracefully: stop accepting new work, drain everything already submitted, and
// exit — without falling into any of the three classic failure modes.
//
// # Failure mode (a): send on a closed channel panics
//
// `close(ch)` is irreversible, and a later `ch <- v` panics with "send on closed
// channel". Submit sends on the jobs channel; Shutdown closes it. If those race,
// Submit can panic. The rule is "only the sender closes, and exactly once" — but
// here many goroutines send (Submit) while one wants to close (Shutdown), so we
// cannot simply make Submit the closer. Instead we fence Submit off first:
//
//  1. Shutdown flips state by closing a `done` channel (via sync.Once, so exactly
//     once and idempotent). `done` is the single source of truth for "shutting
//     down".
//  2. Submit takes a read lock and, under that lock, checks `done`. If `done` is
//     closed it returns ErrShuttingDown; otherwise it sends. The lock makes the
//     check-then-send atomic with respect to the close.
//  3. Shutdown takes the write lock before closing the jobs channel. Holding the
//     write lock means no Submit is mid-send, so closing jobs cannot race a send.
//
// The mutex is the linearisation point: once Shutdown closes `done` and acquires
// the write lock, every future Submit observes `done` and bails, and every past
// Submit has already returned from its send. Only then is it safe to close jobs.
//
// # Failure mode (b): lost in-flight (and queued) messages
//
// Closing the jobs channel or returning from Shutdown before the workers have
// drained the buffer loses queued work. We avoid this by counting work with a
// sync.WaitGroup: NewServer registers the workers (wg.Add(workers)), each worker
// calls wg.Done() only when its `range jobs` loop ends. Ranging over a closed
// channel does not stop at the close — it yields every buffered element first,
// then ends. So closing jobs lets workers finish the queue, and waiting on the
// WaitGroup waits for that drain to complete. No queued or in-flight job is lost.
//
// # Failure mode (c): deadlock / hang
//
// `wg.Wait()` blocks until the count hits zero. If a worker is wedged (a slow or
// stuck `process`), a bare Wait hangs forever — the deploy never completes. The
// fix is the wait-with-timeout idiom: run Wait in a goroutine that closes a
// `finished` channel, then select on `finished` versus `ctx.Done()`. If the
// context's deadline fires first, Shutdown returns ctx.Err() instead of hanging;
// the caller can then escalate (e.g. SIGKILL). The workers are still draining in
// the background, but the operator regains control.
//
// # Extensions (discussed, not implemented)
//
//   - Hard-deadline drain that counts dropped jobs: on ctx timeout, stop draining
//     and report how many queued jobs were abandoned, for observability.
//   - Backpressure on Submit: instead of blocking when the queue is full, add a
//     `default:` arm to the send select and return an ErrQueueFull so callers shed
//     load rather than pile up unbounded latency.
package shutdown

import (
	"context"
	"errors"
	"sync"
)

// ErrShuttingDown is returned by Submit once Shutdown has begun. Submitting to a
// shutting-down server is a clean error, never a panic.
var ErrShuttingDown = errors.New("shutdown: server is shutting down")

// Job is a unit of work submitted to the server.
type Job struct {
	ID string
}

// Server accepts Jobs onto a bounded internal queue processed by a fixed worker
// pool, and supports graceful shutdown.
//
// The zero value is unusable (its channels are nil) — construct one with
// NewServer and call Start before submitting work.
//
// Shutdown coordination uses three primitives:
//
//   - done (closed once via closeOnce): the "shutting down" signal Submit checks.
//   - mu (RWMutex): makes Submit's check-then-send atomic against the close of the
//     jobs channel, so no Submit ever sends on a closed channel.
//   - wg (WaitGroup): counts the workers; Wait returns only once every worker has
//     drained the queue and exited, which is how we guarantee no work is lost.
type Server struct {
	jobs    chan Job
	workers int
	process func(Job)

	mu        sync.RWMutex
	done      chan struct{}
	closeOnce sync.Once
	wg        sync.WaitGroup
}

// NewServer returns a ready Server with the given number of workers, a jobs queue
// of the given capacity, and the per-job processing function. The WaitGroup is
// pre-registered with one count per worker so Shutdown can wait for the pool to
// drain; Start launches the goroutines that satisfy those counts.
func NewServer(workers, queueSize int, process func(Job)) *Server {
	return &Server{
		jobs:    make(chan Job, queueSize),
		workers: workers,
		process: process,
		done:    make(chan struct{}),
	}
}

// Start launches the worker pool. Each worker ranges over the jobs channel,
// processing every job until the channel is closed and drained, then signals the
// WaitGroup it is done. Ranging over a closed channel yields all buffered jobs
// before ending, so closing jobs in Shutdown drains the queue rather than
// dropping it.
func (s *Server) Start() {
	s.wg.Add(s.workers)
	for i := 0; i < s.workers; i++ {
		go func() {
			defer s.wg.Done()
			for j := range s.jobs {
				s.process(j)
			}
		}()
	}
}

// Submit enqueues j for processing, blocking if the queue is full until space is
// available or shutdown begins. It returns ErrShuttingDown once Shutdown has been
// called, and never panics by sending on a closed channel.
//
// The read lock is held across the check of done and the send: while Submit holds
// it, Shutdown cannot acquire the write lock and therefore cannot close the jobs
// channel, so the send is safe. The fast-path check returns early if shutdown has
// already begun; the second select re-checks done so a Submit blocked on a full
// queue unblocks (with ErrShuttingDown) the moment Shutdown signals.
func (s *Server) Submit(j Job) error {
	s.mu.RLock()
	defer s.mu.RUnlock()

	select {
	case <-s.done:
		return ErrShuttingDown
	default:
	}

	select {
	case <-s.done:
		return ErrShuttingDown
	case s.jobs <- j:
		return nil
	}
}

// Shutdown initiates graceful shutdown and blocks until the worker pool has
// drained, or until ctx is done.
//
// It first closes done (exactly once, via closeOnce) so future and blocked
// Submits observe shutdown and return ErrShuttingDown. It then takes the write
// lock — which can only be granted once no Submit holds the read lock, i.e. no
// Submit is mid-send — and closes the jobs channel. With Submit fenced off and
// the channel closed, the workers finish the queued and in-flight jobs and exit.
//
// Wait is run in a goroutine that closes finished; the select then races the
// drain against ctx. If ctx fires first, Shutdown returns ctx.Err() instead of
// hanging on a wedged worker. Shutdown is idempotent and safe to call twice.
func (s *Server) Shutdown(ctx context.Context) error {
	s.closeOnce.Do(func() {
		// Fence off Submit: after this, every Submit observes done and returns.
		close(s.done)

		// Acquire the write lock to guarantee no Submit is mid-send, then close
		// the jobs channel exactly once. This is the only place jobs is closed.
		s.mu.Lock()
		close(s.jobs)
		s.mu.Unlock()
	})

	finished := make(chan struct{})
	go func() {
		s.wg.Wait()
		close(finished)
	}()

	select {
	case <-finished:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
