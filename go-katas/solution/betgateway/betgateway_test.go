package betgateway

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
)

// countingPlacer records how many times Place was called and hands out sequential ids.
type countingPlacer struct {
	calls atomic.Int64
	ids   atomic.Int64
	fail  bool
}

func (p *countingPlacer) Place(_ context.Context, _ string, _ Bet) (PlacedBet, error) {
	p.calls.Add(1)
	if p.fail {
		return PlacedBet{}, fmt.Errorf("downstream unavailable")
	}
	n := p.ids.Add(1)
	return PlacedBet{ID: fmt.Sprintf("bet-%d", n), Status: "accepted"}, nil
}

// fixedLimiter allows or denies every request.
type fixedLimiter struct{ allow bool }

func (l fixedLimiter) Allow(string) bool { return l.allow }

func newRequest(account, key, body string) *http.Request {
	r := httptest.NewRequest(http.MethodPost, "/bets", strings.NewReader(body))
	if account != "" {
		r.Header.Set("X-Account-Id", account)
	}
	if key != "" {
		r.Header.Set("Idempotency-Key", key)
	}
	return r
}

const validBody = `{"selection":"LIV-MUN","stake":500,"odds":2.5}`

func TestPlaceBet_Success(t *testing.T) {
	p := &countingPlacer{}
	g := NewGateway(p, fixedLimiter{allow: true})

	w := httptest.NewRecorder()
	g.ServeHTTP(w, newRequest("acc-1", "key-1", validBody))

	if w.Code != http.StatusCreated {
		t.Fatalf("status = %d, want 201", w.Code)
	}
	var got PlacedBet
	if err := json.NewDecoder(w.Body).Decode(&got); err != nil {
		t.Fatalf("decode body: %v", err)
	}
	if got.ID != "bet-1" || got.Status != "accepted" {
		t.Fatalf("body = %+v, want bet-1/accepted", got)
	}
	if p.calls.Load() != 1 {
		t.Fatalf("Place called %d times, want 1", p.calls.Load())
	}
}

func TestWrongMethod(t *testing.T) {
	g := NewGateway(&countingPlacer{}, fixedLimiter{allow: true})
	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/bets", nil)
	g.ServeHTTP(w, r)
	if w.Code != http.StatusMethodNotAllowed {
		t.Fatalf("status = %d, want 405", w.Code)
	}
}

func TestMissingHeaders(t *testing.T) {
	g := NewGateway(&countingPlacer{}, fixedLimiter{allow: true})
	for _, tc := range []struct{ account, key string }{
		{"", "key-1"},
		{"acc-1", ""},
		{"", ""},
	} {
		w := httptest.NewRecorder()
		g.ServeHTTP(w, newRequest(tc.account, tc.key, validBody))
		if w.Code != http.StatusBadRequest {
			t.Fatalf("account=%q key=%q: status = %d, want 400", tc.account, tc.key, w.Code)
		}
	}
}

func TestMalformedBody(t *testing.T) {
	g := NewGateway(&countingPlacer{}, fixedLimiter{allow: true})
	w := httptest.NewRecorder()
	g.ServeHTTP(w, newRequest("acc-1", "key-1", `{not json`))
	if w.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400", w.Code)
	}
}

func TestInvalidBet(t *testing.T) {
	g := NewGateway(&countingPlacer{}, fixedLimiter{allow: true})
	for _, body := range []string{
		`{"selection":"","stake":500,"odds":2.5}`,  // empty selection
		`{"selection":"X","stake":0,"odds":2.5}`,   // non-positive stake
		`{"selection":"X","stake":500,"odds":0.5}`, // odds below evens
	} {
		w := httptest.NewRecorder()
		g.ServeHTTP(w, newRequest("acc-1", "key-1", body))
		if w.Code != http.StatusUnprocessableEntity {
			t.Fatalf("body=%s: status = %d, want 422", body, w.Code)
		}
	}
}

func TestRateLimited(t *testing.T) {
	p := &countingPlacer{}
	g := NewGateway(p, fixedLimiter{allow: false})
	w := httptest.NewRecorder()
	g.ServeHTTP(w, newRequest("acc-1", "key-1", validBody))
	if w.Code != http.StatusTooManyRequests {
		t.Fatalf("status = %d, want 429", w.Code)
	}
	if p.calls.Load() != 0 {
		t.Fatalf("Place called %d times on a rate-limited request, want 0", p.calls.Load())
	}
}

func TestIdempotentReplay(t *testing.T) {
	p := &countingPlacer{}
	g := NewGateway(p, fixedLimiter{allow: true})

	// First submission: 201, placed.
	w1 := httptest.NewRecorder()
	g.ServeHTTP(w1, newRequest("acc-1", "key-1", validBody))
	if w1.Code != http.StatusCreated {
		t.Fatalf("first status = %d, want 201", w1.Code)
	}

	// Same key again: 200 replay, same id, no second placement.
	w2 := httptest.NewRecorder()
	g.ServeHTTP(w2, newRequest("acc-1", "key-1", validBody))
	if w2.Code != http.StatusOK {
		t.Fatalf("replay status = %d, want 200", w2.Code)
	}
	var b1, b2 PlacedBet
	_ = json.NewDecoder(w1.Body).Decode(&b1)
	_ = json.NewDecoder(w2.Body).Decode(&b2)
	if b1.ID != b2.ID {
		t.Fatalf("replay id = %q, want same as first %q", b2.ID, b1.ID)
	}
	if p.calls.Load() != 1 {
		t.Fatalf("Place called %d times, want 1", p.calls.Load())
	}
}

func TestDifferentKeysPlaceSeparately(t *testing.T) {
	p := &countingPlacer{}
	g := NewGateway(p, fixedLimiter{allow: true})
	for i := 0; i < 3; i++ {
		w := httptest.NewRecorder()
		g.ServeHTTP(w, newRequest("acc-1", fmt.Sprintf("key-%d", i), validBody))
		if w.Code != http.StatusCreated {
			t.Fatalf("key-%d status = %d, want 201", i, w.Code)
		}
	}
	if p.calls.Load() != 3 {
		t.Fatalf("Place called %d times, want 3", p.calls.Load())
	}
}

func TestFailedPlacementIsRetryable(t *testing.T) {
	p := &countingPlacer{fail: true}
	g := NewGateway(p, fixedLimiter{allow: true})

	w1 := httptest.NewRecorder()
	g.ServeHTTP(w1, newRequest("acc-1", "key-1", validBody))
	if w1.Code != http.StatusBadGateway {
		t.Fatalf("failed placement status = %d, want 502", w1.Code)
	}

	// A transient failure must not poison the key — a retry reaches Place again.
	p.fail = false
	w2 := httptest.NewRecorder()
	g.ServeHTTP(w2, newRequest("acc-1", "key-1", validBody))
	if w2.Code != http.StatusCreated {
		t.Fatalf("retry status = %d, want 201", w2.Code)
	}
	if p.calls.Load() != 2 {
		t.Fatalf("Place called %d times, want 2 (initial fail + retry)", p.calls.Load())
	}
}

// TestConcurrentDoubleSubmit is the core race: N simultaneous requests with the SAME Idempotency-Key
// must place exactly ONE bet and all return the same id. A naive check-then-act handler places two
// or more and this fails (and trips -race). Uses a real httptest server so each request runs on its
// own goroutine, gated to start together.
func TestConcurrentDoubleSubmit(t *testing.T) {
	p := &countingPlacer{}
	g := NewGateway(p, fixedLimiter{allow: true})
	srv := httptest.NewServer(g)
	defer srv.Close()

	const requests = 32
	var wg sync.WaitGroup
	start := make(chan struct{})
	var created atomic.Int64
	ids := make([]string, requests)

	for i := 0; i < requests; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			req, _ := http.NewRequest(http.MethodPost, srv.URL+"/bets", strings.NewReader(validBody))
			req.Header.Set("X-Account-Id", "acc-1")
			req.Header.Set("Idempotency-Key", "same-key")
			<-start
			resp, err := srv.Client().Do(req)
			if err != nil {
				t.Errorf("request %d: %v", i, err)
				return
			}
			defer resp.Body.Close()
			if resp.StatusCode == http.StatusCreated {
				created.Add(1)
			}
			var pb PlacedBet
			_ = json.NewDecoder(resp.Body).Decode(&pb)
			ids[i] = pb.ID
		}(i)
	}

	close(start)
	wg.Wait()

	if p.calls.Load() != 1 {
		t.Fatalf("Place called %d times, want exactly 1 (double-submit not deduped)", p.calls.Load())
	}
	if created.Load() != 1 {
		t.Fatalf("%d responses were 201, want exactly 1", created.Load())
	}
	for i, id := range ids {
		if id != "bet-1" {
			t.Fatalf("request %d got id %q, want all bet-1", i, id)
		}
	}
}
