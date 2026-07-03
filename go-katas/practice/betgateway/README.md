# Bet Gateway

> The HTTP front door for bet submission — the endpoint a betslip POSTs to when a punter taps "Place bet". One logical bet must be placed **once**, even when it arrives as many concurrent, identical requests.

## The problem

Build the `http.Handler` that accepts bet submissions. A request is a `POST` with an
`X-Account-Id` header, an `Idempotency-Key` header, and a JSON `Bet` body (`selection`, `stake` in
pennies, `odds`). Punters double-tap, mobile clients retry on flaky connections, and load balancers
replay requests — so the **same** logical bet routinely arrives as two or more concurrent POSTs
carrying the same `Idempotency-Key`. Place it once, charge the stake once.

Rate limiting and the actual placement are dependencies you're given (`Limiter`, `Placer`) — you
write the HTTP glue and the idempotency guard that ties them together.

## Requirements

- Only `POST` is allowed → otherwise `405`.
- `X-Account-Id` and `Idempotency-Key` are both required → otherwise `400`.
- Reject over-limit callers with `429` (ask `limiter.Allow(account)`), before doing real work.
- Malformed JSON body → `400`; a semantically invalid bet (empty selection, `stake <= 0`,
  `odds < 1.0`) → `422`.
- On success call `placer.Place` and return `201` with the JSON `PlacedBet`.
- **Idempotency:** concurrent or repeated requests with the same `Idempotency-Key` must call
  `placer.Place` **exactly once** and all return the **same** `bet_id`. The first placement returns
  `201`; a later replay of an already-placed bet returns `200`.
- A downstream placement error → `502`, and must **not** permanently poison the key (a retry with the
  same key may try again).

## What you implement

- `func NewGateway(placer Placer, limiter Limiter) *Gateway`
- `func (g *Gateway) ServeHTTP(w http.ResponseWriter, r *http.Request)`

`Bet`, `PlacedBet`, `Placer`, and `Limiter` are provided. You design `Gateway`'s internals (the
idempotency store and its synchronisation).

## The real challenge

- **The double-submit is a lost-update race.** The naive `if _, seen := store[key]; !seen { store[key] = place() }`
  lets two concurrent requests both read "not seen" and both call `place()` — the punter is charged
  **twice**. This is the same TOCTOU shape as `priceladder` / `ledger`, but the critical section wraps
  a slow downstream call.
- **Single-flight per key.** Install one in-flight entry per key under a short-held lock, then do the
  single `Place` call with the lock released; concurrent requests for that key wait on the entry and
  return its result. Exactly one `Place`, and unrelated keys never serialise behind each other.
- **Never hold the lock across `Place`.** It's a downstream network call; holding a global mutex
  across it would collapse all concurrency.
- **Cache success, not failure.** Remember completed placements so replays are free; drop the entry on
  error so a transient failure is retryable. (Caching failures forever is the alternative — safer
  against duplicate side effects, worse for the punter. Know the trade-off.)
- **Money angle.** A missed dedup is a duplicated real-money bet; a leaked lock is a stalled betting
  front-end at peak.

## Run

There are no tests here — designing the ones that expose the double-submit is half the exercise.
Write your own `betgateway_test.go` in this package (drive it with `net/http/httptest` — both
`httptest.NewRecorder` for unit cases and `httptest.NewServer` + a gated `sync.WaitGroup` for the
concurrent double-submit), then:

```
cd go-katas/practice && go test -race ./betgateway/
```

> macOS note: a `net/http` test binary links the cgo DNS resolver, which on recent macOS can fail to
> launch (`dyld: missing LC_UUID`). If you hit that, prefix with `CGO_ENABLED=0`:
> `CGO_ENABLED=0 go test -race ./betgateway/`. Linux/CI needs no such flag.

## Reference

Worked solution: `go-katas/solution/betgateway/`.

Extension: make idempotent **replays free of the rate-limit charge** (look the key up before spending
a token), add a per-key TTL so the idempotency store doesn't grow without bound, and return
`Retry-After` on `429`. Then wire a real `Placer` to the `ledger` kata and a real `Limiter` to
`ratelimit`'s token bucket — the gateway is where those two katas meet.
