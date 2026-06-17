package settlepipeline

import (
	"context"
	"sync"
)

// Bet is a single bet to be settled when its market resolves.
type Bet struct {
	ID    string
	Stake int64
}

// Validated is a Bet that has passed validation.
type Validated struct {
	Bet Bet
}

// Reserved is a Validated bet whose payout funds have been reserved.
type Reserved struct {
	Bet      Bet
	Reserved int64
}

// Settled is a Reserved bet that has been settled with a final payout.
type Settled struct {
	Bet    Bet
	Payout int64
}

// Notified is the terminal result: a Settled bet whose customer has been
// notified. The pipeline collects the IDs of these to report success.
type Notified struct {
	Bet Bet
}

// StageFuncs bundles the four per-stage functions so they can be injected — in
// production they hit real services; in tests they force errors, slowness, or
// instrument concurrency. Each takes the upstream value and the (derived)
// pipeline context, returning the next stage's value or a fatal error.
type StageFuncs struct {
	Validate func(context.Context, Bet) (Validated, error)
	Reserve  func(context.Context, Validated) (Reserved, error)
	Settle   func(context.Context, Reserved) (Settled, error)
	Notify   func(context.Context, Settled) (Notified, error)
}

// Pipeline wires the four settlement stages over bounded channels with a
// bounded worker pool per stage. Workers and Buffers are per-stage tuning knobs;
// the zero value of either falls back to a sane default (1) so a partially
// configured pipeline still runs. Real values should come from a benchmark or
// pprof, not a guess — the validate stage is cheap and CPU-bound while reserve
// and notify are IO-bound, so their ideal worker counts differ.
type Pipeline struct {
	fns     StageFuncs
	workers [4]int
	buffers [4]int
}

// New builds a Pipeline from the four stage functions and per-stage worker and
// buffer sizes (indexed validate, reserve, settle, notify). Non-positive sizes
// default to 1.
func New(fns StageFuncs, workers, buffers [4]int) *Pipeline {
	for i := range workers {
		if workers[i] < 1 {
			workers[i] = 1
		}
		if buffers[i] < 1 {
			buffers[i] = 1
		}
	}
	return &Pipeline{fns: fns, workers: workers, buffers: buffers}
}

// Run drives every bet through validate → reserve → settle → notify and returns
// the IDs of the successfully notified bets, or the first fatal error.
//
// # Wiring
//
// Run derives a cancellable context from ctx and builds the graph with Stage:
// a feeder goroutine owns and closes the head channel (the only producer into
// it), then four Stage calls chain output→input. Each Stage owns and closes its
// own output/error channels. A fan-in goroutine multiplexes the four stages'
// error channels onto one, so a single select can watch for the first error
// alongside the final results.
//
// # End-to-end cancellation and first-error short-circuit
//
// Run uses ONE derived context (cancel) for the feeder and all four stages. Two
// things trip that cancel: the caller's ctx being cancelled (propagated because
// the derived context is a child), and the first fatal error from any stage. On
// either, cancel() unblocks every in-flight send/receive in every stage at once
// — upstream stages stop feeding, downstream stops pulling, and Run returns
// promptly with ctx.Err() or the stage error. This is the clean way to stop a
// pipeline: you tear it down through the context every stage already watches,
// rather than trying to signal each stage individually.
//
// # Drain vs abandon
//
// On the happy path Run ranges the notify output to completion: when the feeder
// closes the head channel, each stage drains and WaitGroup-then-closes its
// output in order, the notify output closes, the range ends, and every bet has
// been collected — a graceful drain with nothing lost. On the error/cancel path
// Run abandons in-flight work deliberately (cancel, then drain the remaining
// channels only to let the stages' workers unblock and exit) — completeness is
// sacrificed for a prompt, leak-free shutdown. Either way no goroutine survives.
func (p *Pipeline) Run(ctx context.Context, bets []Bet) ([]string, error) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	// Head channel: the feeder is its sole producer, so the feeder closes it.
	head := make(chan Bet, p.buffers[0])
	go func() {
		defer close(head)
		for _, b := range bets {
			select {
			case <-ctx.Done():
				return
			case head <- b:
			}
		}
	}()

	// Chain the four stages. Each Stage owns and closes its out/err channels.
	validated, e0 := Stage(ctx, head, p.workers[0], p.buffers[0], p.fns.Validate)
	reserved, e1 := Stage(ctx, validated, p.workers[1], p.buffers[1], p.fns.Reserve)
	settled, e2 := Stage(ctx, reserved, p.workers[2], p.buffers[2], p.fns.Settle)
	notified, e3 := Stage(ctx, settled, p.workers[3], p.buffers[3], p.fns.Notify)

	// Fan-in the four error streams onto one. Each forwarder selects on
	// ctx.Done() so a torn-down pipeline cannot park it; a closer closes the
	// merged channel exactly once after all forwarders finish.
	errc := mergeErrors(ctx, e0, e1, e2, e3)

	// Consume the final stage and watch for the first error simultaneously.
	settledIDs := make([]string, 0, len(bets))
	for notified != nil || errc != nil {
		select {
		case n, ok := <-notified:
			if !ok {
				notified = nil // notify output drained and closed.
				continue
			}
			settledIDs = append(settledIDs, n.Bet.ID)
		case err, ok := <-errc:
			if !ok {
				errc = nil // all stages finished; no error occurred.
				continue
			}
			if err != nil {
				// First fatal error: short-circuit. cancel() tears every
				// stage down; then drain so their workers unblock and the
				// closers can run (no leak), discarding the rest.
				cancel()
				drain(notified)
				drainErrors(errc)
				return nil, err
			}
		}
	}

	// Both channels closed with no error. If the caller's ctx was cancelled
	// mid-flight, surface that rather than a partial success.
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	return settledIDs, nil
}

// mergeErrors fans the per-stage error channels into one, forwarding every error
// (selecting on ctx.Done() so a torn-down pipeline cannot leak a forwarder) and
// closing the merged channel exactly once after all forwarders return.
func mergeErrors(ctx context.Context, srcs ...<-chan error) <-chan error {
	out := make(chan error, len(srcs))
	var wg sync.WaitGroup
	wg.Add(len(srcs))
	for _, src := range srcs {
		go func(src <-chan error) {
			defer wg.Done()
			for {
				select {
				case <-ctx.Done():
					return
				case err, ok := <-src:
					if !ok {
						return
					}
					select {
					case <-ctx.Done():
						return
					case out <- err:
					}
				}
			}
		}(src)
	}
	go func() {
		wg.Wait()
		close(out)
	}()
	return out
}

// drain consumes a channel until it is closed, discarding values. After cancel()
// it lets a stage's workers finish their in-flight sends so the stage's closer
// can run — the leak-free way to abandon the rest of the pipeline.
func drain[T any](ch <-chan T) {
	for ch != nil {
		if _, ok := <-ch; !ok {
			return
		}
	}
}

// drainErrors is drain specialised for the merged error channel.
func drainErrors(ch <-chan error) {
	for range ch {
	}
}
