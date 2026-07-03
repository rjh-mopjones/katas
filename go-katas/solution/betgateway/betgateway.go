// Package betgateway implements the HTTP front door for bet submission: the endpoint a betslip POSTs
// to when a punter taps "Place bet".
//
// It is the one kata in this module that is about the *edge* of the system — parsing an HTTP request,
// choosing status codes, and gluing together two concerns you have already built as standalone katas
// (idempotency and rate limiting) into a single http.Handler. The concurrency-correctness heart of it
// is the idempotency guard.
//
// # The money bug: the double-submit
//
// Punters double-tap. Mobile clients retry on a flaky connection. A load balancer replays a request
// whose response it never saw. So the *same* logical bet arrives as two (or ten) concurrent POSTs
// carrying the same Idempotency-Key. If the handler does the obvious check-then-act —
//
//	if _, seen := store[key]; !seen { store[key] = place(bet) }
//
// two requests race between the read and the write, both see "not seen", and both call place(): the
// punter is on the hook for *twice* the stake. That is a lost-update / TOCTOU race, the same shape as
// the priceladder and ledger katas, but here the critical section wraps a slow downstream call.
//
// # The fix: single-flight per key
//
// Gateway keeps one in-flight entry per Idempotency-Key. The first request for a key installs the
// entry under a short-held mutex and then — with the lock released — performs the single Place call;
// concurrent requests for the same key find the existing entry, block on its ready channel, and
// return the identical result. Exactly one Place happens per key, and the mutex is never held across
// the downstream call, so unrelated keys never serialise behind each other.
//
//   - Successful placements are remembered, so a later retry with the same key replays the same bet
//     id (HTTP 200) instead of placing again (HTTP 201 the first time).
//   - A *failed* placement removes the entry, so the punter can retry the same key — an idempotency
//     record should not permanently poison a key on a transient downstream error. (Caching failures
//     forever is the alternative; it is safer against duplicate side effects but worse for the
//     punter. Know the trade-off.)
//
// # What is delegated, and why
//
// Rate limiting and the actual bet placement are injected as interfaces (Limiter, Placer) rather than
// reimplemented here: the token-bucket lives in the ratelimit kata, and the book/ledger lives
// downstream. This keeps the kata about the HTTP + idempotency glue, and makes the handler trivially
// testable with stubs. It is also the honest production shape — a gateway composes services; it does
// not own them.
package betgateway

import (
	"context"
	"encoding/json"
	"net/http"
	"sync"
)

// Bet is a submitted betslip. Stake is in integer pennies (never float money); Odds is decimal.
type Bet struct {
	Selection string  `json:"selection"`
	Stake     int64   `json:"stake"`
	Odds      float64 `json:"odds"`
}

// PlacedBet is the result of accepting a bet. ID is assigned downstream.
type PlacedBet struct {
	ID     string `json:"bet_id"`
	Status string `json:"status"`
}

// Placer places a validated bet against the downstream book and returns its assigned id. It is the
// only side-effecting dependency; the idempotency guard exists to call it exactly once per key.
type Placer interface {
	Place(ctx context.Context, accountID string, bet Bet) (PlacedBet, error)
}

// Limiter reports whether an account may place another bet right now (e.g. a per-account token
// bucket). Returning false yields HTTP 429.
type Limiter interface {
	Allow(accountID string) bool
}

// Gateway is the http.Handler that accepts bet submissions. Safe for concurrent use.
type Gateway struct {
	placer  Placer
	limiter Limiter

	mu       sync.Mutex
	inflight map[string]*entry // keyed by Idempotency-Key
}

// entry is a single-flight slot: the first request for a key fills bet/err and closes ready; waiters
// block on ready and read the shared result.
type entry struct {
	ready chan struct{}
	bet   PlacedBet
	err   error
}

// NewGateway builds a Gateway over a Placer and a Limiter.
func NewGateway(placer Placer, limiter Limiter) *Gateway {
	return &Gateway{
		placer:   placer,
		limiter:  limiter,
		inflight: make(map[string]*entry),
	}
}

// ServeHTTP handles POST /… with headers X-Account-Id and Idempotency-Key and a JSON Bet body.
//
// Status codes: 405 wrong method; 400 missing headers / malformed body; 422 semantically invalid bet;
// 429 rate limited; 201 bet placed; 200 idempotent replay of an already-placed bet; 502 downstream
// placement error.
func (g *Gateway) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	account := r.Header.Get("X-Account-Id")
	key := r.Header.Get("Idempotency-Key")
	if account == "" || key == "" {
		http.Error(w, "missing X-Account-Id or Idempotency-Key", http.StatusBadRequest)
		return
	}

	// Rate-limit before doing any work — a rejected caller must be cheap. (An idempotent *replay*
	// still costs a token here; if you wanted replays to be free you would look up the key first.)
	if !g.limiter.Allow(account) {
		http.Error(w, "rate limit exceeded", http.StatusTooManyRequests)
		return
	}

	var bet Bet
	if err := json.NewDecoder(r.Body).Decode(&bet); err != nil {
		http.Error(w, "malformed request body", http.StatusBadRequest)
		return
	}
	if bet.Selection == "" || bet.Stake <= 0 || bet.Odds < 1.0 {
		http.Error(w, "invalid bet", http.StatusUnprocessableEntity)
		return
	}

	placed, created, err := g.submit(r.Context(), account, key, bet)
	if err != nil {
		http.Error(w, "placement failed", http.StatusBadGateway)
		return
	}

	status := http.StatusOK
	if created {
		status = http.StatusCreated
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(placed)
}

// submit runs the single-flight idempotency guard. It returns the placed bet, whether THIS call
// created it (true) versus replayed an existing result (false), and any placement error.
func (g *Gateway) submit(ctx context.Context, account, key string, bet Bet) (PlacedBet, bool, error) {
	g.mu.Lock()
	if e, ok := g.inflight[key]; ok {
		g.mu.Unlock()
		<-e.ready // wait for the first request for this key to finish
		return e.bet, false, e.err
	}
	e := &entry{ready: make(chan struct{})}
	g.inflight[key] = e
	g.mu.Unlock()

	// The lock is NOT held across Place — unrelated keys proceed in parallel.
	e.bet, e.err = g.placer.Place(ctx, account, bet)

	if e.err != nil {
		// Drop the entry so a transient failure can be retried under the same key.
		g.mu.Lock()
		delete(g.inflight, key)
		g.mu.Unlock()
	}
	close(e.ready)
	return e.bet, true, e.err
}
