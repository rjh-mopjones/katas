# Go Katas — concurrency & low-latency

Concurrency-correctness katas framed as components of a low-latency sports-betting trading platform
(the kind of thing you'd pair on for a Go engineer role: feeds, prices, bets, settlement,
shutdown). Same two-tree model as the other languages: a worked `solution/` (full implementation +
tests + interview-grade doc comments — the answer key, always GREEN and `-race` clean) and a blank
`practice/` skeleton you implement from scratch.

> **Write your own tests.** The practice side ships *without* tests on purpose — designing the
> concurrency/edge cases that expose the bug is half the exercise. Run them under `-race`.

## Layout

Two Go modules, identical package layout:

```
go-katas/
├── solution/     module .../go-katas/solution   — reference impl + tests (GREEN, -race clean)
└── practice/     module .../go-katas/practice    — blank skeletons + per-kata README, NO tests
```

Each kata is a package in both trees at the same path (`solution/pricecache/` ↔ `practice/pricecache/`).
Start from the practice README; diff against the solution twin when stuck.

## Katas

| # | Package | Scenario | The trap to get right |
|---|---------|----------|-----------------------|
| 1 | [`pricecache`](practice/pricecache/) | Feed writes latest prices; many handlers read them. | Data race on a bare map — `go test -race` proves it; guard with `RWMutex`. |
| 2 | [`oddsfeed`](practice/oddsfeed/) | Worker pool consuming an odds feed. | Goroutine leak — workers must exit on ctx-cancel *and* channel-close. |
| 3 | [`feedchannel`](practice/feedchannel/) | Channel-based price fan-out broker. | Send-on-closed panic, nil-channel hang, bounded backpressure. |
| 4 | [`priceladder`](practice/priceladder/) | Relative odds adjustments to a market price. | Lost update — the read-modify-write must be one atomic critical section. |
| 5 | [`betmachine`](practice/betmachine/) | Bet lifecycle state machine under duplicate events. | Illegal transitions & double-firing (double payout); idempotent + guarded. |
| 6 | [`venuefanin`](practice/venuefanin/) | Merge many venue feeds into one stream. | No drop / no dup; WaitGroup-then-close; bounded buffer, not unbounded growth. |
| 7 | [`settlement`](practice/settlement/) | Pay out winning bets via a downstream dependency. | Context propagation — caller cancel/timeout must reach downstream calls. |
| 8 | [`shutdown`](practice/shutdown/) | Graceful shutdown of a bet-processing server. | No deadlock, no lost in-flight jobs, no send-on-closed; close once. |

Difficulty varies: `pricecache` / `priceladder` are the most direct; `feedchannel`, `venuefanin`,
and `shutdown` involve the subtler close/coordination ordering.

## Commands

Run from `go-katas/` (a `Makefile` wraps these):

```bash
# practice (the module you implement)
cd practice && go test -race ./...        # or: make practice-race
cd practice && go vet ./...               #     make practice-vet

# solution (reference answer key)
cd solution && go test -race ./...        #     make solution-race

# one kata only
cd practice && go test -race ./pricecache/

# benchmarks (where a kata's extension adds one)
cd solution && go test -bench=. ./pricecache/
```

Requires Go 1.22+. Standard library only — no third-party dependencies.

## Each kata's extension is a real design step

The READMEs end with an extension (make the read path lock-free with `atomic.Pointer` and benchmark
it; add idempotency keys + an event log; add per-venue coalescing under backpressure; bounded-
concurrency settlement that cancels siblings on first failure; hard-deadline drain). Treat it as the
"now extend it to X" half of a pairing interview.
