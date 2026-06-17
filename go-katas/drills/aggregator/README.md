# Aggregator drill

A cold, self-directed Go drill: build a low-latency market-data aggregator from scratch
through eight escalating stages. See [`SPEC.md`](SPEC.md) for the stages.

You write everything; `aggregator.go` ships only compiling stubs so you have a starting shape
for Stage 0. Reshape it freely as the stages grow.

A worked reference covering all eight stages lives in [`solution/`](solution/) (its own module —
`cd solution && go test -race ./...`). **Drill each stage cold first**, then diff against it. It's
an answer key, not the only valid design — the read path is lock-free (RWMutex only to create a
market + a per-market `atomic.Pointer[MarketView]`), staleness uses an injectable clock + a
sweeper, and subscriptions use latest-wins backpressure with exactly-once shutdown.

Standard library only. (You *may* pull in `golang.org/x/sync` later if you decide a stage
calls for it — it is not in `go.mod`; add it yourself if and when you choose.)

Go 1.22+ (`go.mod` is pinned to the locally installed toolchain).

## Commands

Run these from this directory, repeatedly, after each stage:

```bash
go build ./...           # compiles
go vet ./...             # static checks clean
go test ./...            # your unit tests
go test -race ./...      # concurrency correctness (Stage 1 onward)
go test -bench . ./...   # read-path benchmark (Stage 7)
```

Useful while drilling:

```bash
go test -run TestName ./...                 # one test
go test -race -run TestConcurrent ./...     # one concurrency test under the detector
go test -bench BenchmarkGet -benchmem ./... # benchmark with allocs/op
```

Profiling, when you get to it (Stage 7):

```bash
go test -bench BenchmarkGet -cpuprofile cpu.out -memprofile mem.out ./...
go tool pprof cpu.out      # or mem.out
```

## Layout

```
aggregator/
├── go.mod
├── aggregator.go   # stubs only — your code goes here (and any files you add)
├── SPEC.md         # the staged drill
└── README.md
```
