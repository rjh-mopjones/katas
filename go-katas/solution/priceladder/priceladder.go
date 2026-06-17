// Package priceladder implements a concurrency-safe market price ladder where
// feed updates apply *relative* odds adjustments to a market's price.
//
// The shape of the problem is the canonical lost-update exercise. A relative
// adjustment — "shorten this market's price by 0.05" — is a read-modify-write
// (RMW): read the current price, add the delta, write the new price back. When
// many feed goroutines apply adjustments to the same market concurrently, a
// non-atomic RMW silently loses updates:
//
//   - Goroutine A reads price 2.00.
//   - Goroutine B reads price 2.00 (before A has written).
//   - A adds its delta (+0.10) and writes 2.10.
//   - B adds its delta (+0.05) to the 2.00 *it* read and writes 2.05.
//
// A's update is gone. The final price is 2.05, but the correct, all-deltas-applied
// answer is 2.15. The two adjustments raced on a shared cell and one overwrote the
// other. This is not a cosmetic glitch: a price that does not reflect every
// adjustment mis-prices risk — in a real-money market that is a correctness defect
// that can be exploited or simply lose money.
//
// # Why the lock must span the whole read-modify-write
//
// The subtle, interview-grade point is that locking is necessary but not
// sufficient if the lock is scoped wrong. A "get-then-set" built from two
// separately-locked operations — lock, read; unlock; (add delta); lock, write;
// unlock — still loses updates, because another goroutine can slip in between the
// read and the write and the same interleaving above replays. The lock must cover
// the *entire* RMW: read, add, and write all happen inside one critical section so
// the trio is indivisible. That is exactly what Adjust does below.
//
// # Why plain float64 + atomic does not work for add
//
// You cannot make this lock-free with a bare atomic on a float64. The sync/atomic
// package has no atomic floating-point add: addition over IEEE-754 bits is not a
// hardware atomic. The lock-free recipe is a compare-and-swap loop on the bit
// pattern (atomic.Uint64 holding math.Float64bits, loop: load → compute sum →
// CompareAndSwap, retry on contention). That works but is fiddly and still spins
// under contention.
//
// # Named alternatives (the extension)
//
//   - Fixed-point integer ticks + atomic.AddInt64. Represent money as an integer
//     number of ticks (e.g. hundredths) rather than a float. Integer addition *is*
//     an atomic primitive, so Adjust becomes a single wait-free atomic.AddInt64 on
//     a per-market counter — a genuinely lock-free fast path with no CAS loop, and
//     it also sidesteps float rounding error. This is the recommended extension.
//   - Striped / sharded locks. Keep the mutex but shard markets across N locks
//     (hash the market name to a stripe) so adjustments to *different* markets do
//     not contend on one global lock. Cuts contention without changing the model.
//
// PriceLadder below uses the simplest correct design: a single sync.Mutex guarding
// a map[string]float64, with Adjust performing the read+add+write inside one held
// lock.
package priceladder

import "sync"

// Ladder holds the current price for each market and applies relative and
// absolute updates to them in a concurrency-safe way.
//
// The zero value is not usable; construct one with NewLadder.
type Ladder struct {
	mu     sync.Mutex
	prices map[string]float64
}

// NewLadder returns an empty, ready-to-use Ladder.
func NewLadder() *Ladder {
	return &Ladder{prices: make(map[string]float64)}
}

// Adjust applies a relative change to a market's price: the current price for
// market is increased by delta (treating an absent market as starting from 0).
//
// This is the crux of the kata. The read, the add, and the write all happen
// inside the *one* held lock, so the read-modify-write is atomic with respect to
// other Adjust/Set calls. Under concurrency every delta is therefore applied
// exactly once and none are lost. Splitting this into a locked read and a separate
// locked write would reopen the lost-update race even though both halves are
// individually locked.
func (l *Ladder) Adjust(market string, delta float64) {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.prices[market] += delta
}

// Set assigns an absolute price to a market, overwriting any previous value.
func (l *Ladder) Set(market string, price float64) {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.prices[market] = price
}

// Price returns the current price for market and whether it is known.
//
// On a miss it returns 0 and false.
func (l *Ladder) Price(market string) (float64, bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	p, ok := l.prices[market]
	return p, ok
}
