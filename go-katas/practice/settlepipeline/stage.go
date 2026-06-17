package settlepipeline

import (
	"context"
)

// Stage runs fn over every value received on in using a bounded pool of
// `workers` goroutines, emitting each successful result on the returned output
// channel (buffered to `buffer`) and each error on the returned error channel.
//
// Both returned channels are owned by Stage and closed by Stage exactly once,
// after every worker has finished (WaitGroup-then-close). The caller must drain
// both — or cancel ctx — so that workers do not block on a full output and leak.
//
// Every channel operation (receive from in, send to out, send to errc) must
// select on ctx.Done(), so a cancelled context unblocks the stage promptly and
// no worker survives cancellation.
func Stage[I, O any](
	ctx context.Context,
	in <-chan I,
	workers, buffer int,
	fn func(context.Context, I) (O, error),
) (<-chan O, <-chan error) {
	panic("TODO: implement")
}
