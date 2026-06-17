// Package settlepipeline implements a staged, bounded-concurrency settlement
// pipeline. When a market settles, every bet flows through four stages —
// validate → reserve funds → settle → notify — each backed by a bounded worker
// pool and connected to the next by a bounded channel. The whole thing is a
// fan-out/fan-in graph that must honour three properties at once: end-to-end
// context cancellation (a caller that gives up stops all in-flight work
// promptly), first-error short-circuit (one fatal error tears the pipeline down
// instead of letting the rest grind on), and a clean drain on success (no lost
// items, every channel closed exactly once in order, no leaked goroutine).
//
// # Stage: a reusable bounded-concurrency step
//
// Stage is the single building block. It reads from an input channel, applies a
// caller-supplied fn with a fixed number of worker goroutines, and writes the
// results to a bounded output channel. The bound on the workers caps CPU/IO
// concurrency for the step; the bound on the output buffer is backpressure
// between this step and the next.
//
// The lifecycle is the WaitGroup-then-close idiom, the same rule the fan-in
// kata turns on: the output is closed exactly once, and only after every worker
// has returned. The close cannot live inside a worker — closing early would
// drop results still queued in sibling workers (or panic on a send to a closed
// channel), and closing from more than one worker panics on the second close
// because close is not idempotent. So each worker calls wg.Done() on exit and a
// dedicated closer goroutine does wg.Wait() then close(out): single owner,
// exactly once, after the last send.
//
// # Why every channel op selects on ctx.Done()
//
// Each worker selects on ctx.Done() on BOTH the receive (from in) and the send
// (to out and to errc). The send side is the one people forget. If the consumer
// of out has stopped — because the caller cancelled, or a downstream stage
// short-circuited on an error — a bare `out <- v` on a full buffer parks the
// worker forever. The closer's wg.Wait() then never completes, out is never
// closed, and the goroutine leaks. Racing every send against ctx.Done() lets a
// worker abandon its value and return the instant the context is cancelled, so
// cancellation reaches every in-flight step and no orphaned work continues after
// the caller has given up.
//
// # Bounded buffers = backpressure
//
// The output buffer has a fixed capacity. That bound is the point: when the next
// stage is slower, this stage's sends block, its workers stop pulling from in,
// and the slowness propagates upstream to the feeder. Memory stays bounded by
// the buffer size regardless of how fast an early stage produces. An unbounded
// buffer would not remove backpressure — it would let a fast stage balloon
// memory ahead of a slow one until the process is OOM-killed. Keep the bound;
// pprof/benchmarks (not guesswork) should drive the actual worker and buffer
// numbers per stage.
package settlepipeline

import (
	"context"
	"sync"
)

// Stage runs fn over every value received on in using a bounded pool of
// `workers` goroutines, emitting each successful result on the returned output
// channel (buffered to `buffer`) and each error on the returned error channel.
//
// Both returned channels are owned by Stage and closed by Stage exactly once,
// after every worker has finished (WaitGroup-then-close). The caller must drain
// both — or cancel ctx — so that workers do not block on a full output and leak.
//
// Every channel operation (receive from in, send to out, send to errc) selects
// on ctx.Done(), so a cancelled context unblocks the stage promptly and no
// worker survives cancellation. With workers <= 0 it defaults to one worker so
// the stage always makes progress.
func Stage[I, O any](
	ctx context.Context,
	in <-chan I,
	workers, buffer int,
	fn func(context.Context, I) (O, error),
) (<-chan O, <-chan error) {
	if workers < 1 {
		workers = 1
	}
	out := make(chan O, buffer)
	errc := make(chan error, buffer)

	var wg sync.WaitGroup
	wg.Add(workers)

	for i := 0; i < workers; i++ {
		go func() {
			defer wg.Done()
			for {
				// Receive: stop on cancellation or when the upstream input
				// has drained and closed.
				var (
					v  I
					ok bool
				)
				select {
				case <-ctx.Done():
					return
				case v, ok = <-in:
					if !ok {
						return
					}
				}

				res, err := fn(ctx, v)
				if err != nil {
					// Send the error, but race ctx.Done() — once the
					// pipeline is tearing down nobody may be reading errc.
					select {
					case <-ctx.Done():
						return
					case errc <- err:
					}
					continue
				}

				// Send the result, racing ctx.Done() so a full output with a
				// departed consumer cannot park the worker forever.
				select {
				case <-ctx.Done():
					return
				case out <- res:
				}
			}
		}()
	}

	// Closer: only after every worker has returned (so every value it would
	// send has been sent) do we close both channels, here and exactly once.
	go func() {
		wg.Wait()
		close(out)
		close(errc)
	}()

	return out, errc
}
