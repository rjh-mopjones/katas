// Package venuefanin implements a fan-in aggregator that merges price quotes
// from many venue feeds (each a channel) onto one downstream channel.
//
// FanIn is the classic Go fan-in pattern: one forwarding goroutine per source,
// a single shared bounded output channel, a sync.WaitGroup that counts the
// forwarders, and one closer goroutine that closes the output once every
// forwarder has finished. The kata is about getting three things right at the
// same time — no drop, no duplicate, and clean termination — without leaking a
// goroutine and without unbounded memory growth.
//
// # WaitGroup-then-close (why the close lives in its own goroutine)
//
// Each forwarder owns exactly one source and copies every value it receives onto
// the output. The output must be closed exactly once, and only after the last
// value has been forwarded — so the closer cannot live inside any single
// forwarder:
//
//   - Closing too early drops in-flight quotes. If a forwarder closed the output
//     when its own source drained, quotes still queued in the other forwarders
//     would hit a send on a closed channel.
//   - Closing from multiple goroutines panics. close is not idempotent; the
//     second close("send/close on closed channel") panics. "Only close once, and
//     only after all senders are done" is the rule.
//
// The idiom that satisfies both: every forwarder calls wg.Done() as it exits; a
// dedicated goroutine does wg.Wait() then close(out). wg.Wait() blocks until the
// last forwarder has returned (so every value it was going to send has been
// sent), and the close happens from a single owner exactly once. The consumer's
// `range out` then terminates cleanly. Every value from every source appears on
// the output exactly once: forwarders neither drop (they block until the value
// is taken or ctx is done) nor duplicate (each value is received once and
// forwarded once).
//
// # Bounded buffer = backpressure (the bug this kata targets)
//
// The output channel has a fixed capacity (bufferSize). That bound is the whole
// point: it is backpressure. When the consumer is slower than the venues, the
// buffer fills, forwarder sends block, and the blocked receive on each source
// stops pulling — the slowness propagates back to the fast venues and they wait.
// Memory stays bounded by bufferSize regardless of how fast the venues produce.
//
// The tempting "fix" for a slow consumer — spool overflow into an unbounded slice
// or queue so sends never block — is the classic memory blowup: a fast venue can
// outpace the consumer indefinitely and the backing slice grows without limit
// until the process is OOM-killed. An unbounded buffer does not remove
// backpressure, it just hides it until you run out of RAM. Keep the bound.
//
// # Why every send also selects on ctx.Done()
//
// A forwarder must select on ctx.Done() on BOTH sides: when receiving from its
// source AND when sending to the output. The send side is the one people forget.
// If the consumer goes away (ctx cancelled, request abandoned) while the output
// buffer is full, a bare `out <- q` blocks forever — the forwarder parks holding
// its source and its stack, and the closer's wg.Wait() never completes, so the
// output is never closed either. That is a goroutine leak. Selecting the send
// against ctx.Done() lets the forwarder abandon the value and return when the
// consumer is gone, which lets wg.Wait() complete and the output close.
//
// # Backpressure vs load-shed (the policy trade-off)
//
// Blocking the venues (backpressure) guarantees completeness but couples every
// venue to the slowest consumer — a stalled consumer stalls all feeds. The
// alternative is load-shed: add a `default:` arm to the send select so a full
// buffer drops the quote and the forwarder moves on, favouring liveness and
// bounded latency at the cost of data loss. For odds/price feeds, blind dropping
// is usually the wrong load-shed: you may drop the newest tick and deliver a
// stale one. The better shed for quotes is latest-wins coalescing — keep only the
// most recent quote per market and overwrite older un-sent ones — so under
// pressure you lose intermediate ticks but never serve a stale price. See the
// README extension for per-venue/per-market coalescing under backpressure.
package venuefanin

import (
	"context"
	"sync"
)

// Quote is an immutable price quote from a single venue for a single market.
type Quote struct {
	Venue  string
	Market string
	Price  float64
}

// FanIn merges the given source channels onto a single output channel and
// returns the receive-only output for the consumer to range over.
//
// The output channel is buffered to bufferSize, which is the bounded
// backpressure window: forwarders proceed without blocking while fewer than
// bufferSize quotes are in flight, and block (subject to ctx) once it is full.
//
// One goroutine per source forwards every received quote onto the output,
// selecting on ctx.Done() on both the receive and the send so neither a stalled
// source nor a departed consumer can leak it. A sync.WaitGroup counts the
// forwarders; a single closer goroutine waits for all of them and then closes the
// output exactly once. The output therefore closes when every source has drained
// OR the context is cancelled, and every quote that is forwarded appears exactly
// once (no drop, no duplicate). With no sources, the output is closed immediately.
func FanIn(ctx context.Context, bufferSize int, sources ...<-chan Quote) <-chan Quote {
	out := make(chan Quote, bufferSize)

	var wg sync.WaitGroup
	wg.Add(len(sources))

	for _, src := range sources {
		go func(src <-chan Quote) {
			defer wg.Done()
			for {
				// Receive: stop if the consumer/caller cancelled, or if this
				// source has drained and closed.
				select {
				case <-ctx.Done():
					return
				case q, ok := <-src:
					if !ok {
						return
					}
					// Send: the send must also race ctx.Done(), or a full output
					// buffer with a departed consumer parks this forwarder
					// forever (goroutine leak) and the closer never runs.
					select {
					case <-ctx.Done():
						return
					case out <- q:
					}
				}
			}
		}(src)
	}

	// Closer: only after every forwarder has returned (so every value it would
	// send has been sent) do we close the output, and only here, exactly once.
	go func() {
		wg.Wait()
		close(out)
	}()

	return out
}
