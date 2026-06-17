# Drill: market-data aggregator (staged, cold)

A self-directed, build-it-yourself drill for a low-latency sports-betting trading
firm. One package, one problem, escalated through eight stages. **You write all the
real code.** The scaffold ships only stubs.

How to run it as a drill:

- Time-box each stage (≈20–40 min). Resist jumping ahead — each stage is meant to
  force a refactor of the last.
- After each stage: `go build ./...`, `go vet ./...`, `go test ./...`, and from
  Stage 1 on, `go test -race ./...`. Keep it green before moving on.
- Say your design decisions out loud as if pairing. The interview scores *why* you
  chose a structure, not just that it works.
- No reference solution exists in this folder. That's deliberate.

The stages give you the requirement and the **senior signal** each one probes. They
deliberately do **not** tell you which type, primitive, or pattern to reach for —
picking that, and defending it, is the whole point.

---

## Stage 0 — Core, single-threaded

**Requirement.** Ingest `PriceUpdate`s and keep the current best back/lay price per
market. Expose a way to feed updates in and a `Get(market)` that returns the current
best view (and whether one exists). Single goroutine; no concurrency yet.

**Senior signal.** Can you model the domain cleanly before adding machinery? What
does "best" back vs "best" lay actually mean, and how do you handle the first update
for a market, an unknown market, and an update that doesn't improve the current best?
Get the data model and the happy path crisp — everything later is built on this.

---

## Stage 1 — Concurrent producers

**Requirement.** Multiple venue feeds call in concurrently. The aggregator must be
correct under `go test -race` with many writers (and at least some readers) hammering
it at once.

**Senior signal.** Do you actually understand what "data race" means in Go's memory
model — not just "add something so the detector goes quiet"? Write the concurrency
test that *would* catch the race first, watch it fail (or flag), then make it pass.
Be able to explain what `-race` is and is not proving.

---

## Stage 2 — Hot-path reads

**Requirement.** Reads are the hot path (many handlers asking for the current price);
they must not be blocked by writers any more than necessary. Reason explicitly about
a reader-writer lock versus a lock-free read path (copy-on-write / an atomically
swapped snapshot), then pick one and implement it.

**Senior signal.** Can you articulate the trade-off — read:write ratio, contention,
allocation cost of copy-on-write, staleness of a snapshot, complexity — and justify
your choice for *this* read-heavy workload rather than reciting a default? Either
choice can be correct; the reasoning is what's assessed.

---

## Stage 3 — Staleness / TTL

**Requirement.** A price older than some TTL is stale and must stop being served:
`Get` should not return an expired price. Decide how expiry happens (on read, on a
sweep, both) and how you represent "now".

**Senior signal.** Time handling under test. How do you make staleness logic
*deterministic to test* without sleeping in tests? Do you make the clock injectable?
Do you leak a background sweeper goroutine, and if you run one, how does it stop
(this connects to Stage 6)?

---

## Stage 4 — Subscriptions / push

**Requirement.** A caller can subscribe to a market and receive pushed updates as new
prices arrive, then unsubscribe. One slow subscriber must not block ingestion or the
other subscribers.

**Senior signal.** Isolation between consumers. What happens when a subscriber stops
reading? Do you understand the failure modes of fan-out over channels (a blocked send
stalling the publisher, a leaked subscriber goroutine, sending on a channel after
unsubscribe)? How does a subscriber cleanly leave without racing the publisher?

---

## Stage 5 — Backpressure

**Requirement.** Bound the buffering per subscriber (or globally) so a slow or stuck
consumer cannot grow memory without limit. Choose a policy — block the producer, drop
oldest/newest, or coalesce to latest-wins — and justify it for live odds.

**Senior signal.** Do you reach for the policy that fits the domain rather than the
first one that compiles? For fast-moving odds, what does a consumer actually want when
it falls behind — every tick, or the freshest price? Name the cost of your choice
(latency, lost data, head-of-line blocking) and when you'd pick differently.

---

## Stage 6 — Graceful shutdown

**Requirement.** Stop cleanly on context cancellation. Decide deliberately whether
in-flight work is drained or abandoned, and make that decision explicit. No goroutine
leaks; no panics from sending on or closing channels that are already closed.

**Senior signal.** The ownership question: who closes what, and exactly once? Can you
shut down with producers and subscribers still active without a send-on-closed panic
or a deadlock? Prove no leak (e.g. goroutine count returns to baseline). State what
"drain vs abandon" means for real-money correctness.

---

## Stage 7 — Prove it

**Requirement.** Back the whole thing with tests: table-driven unit tests for the core
logic, a concurrency test that runs under `-race`, and a benchmark on the read path.
Note in comments where you'd reach for `pprof` and what you'd look for.

**Senior signal.** Do your tests pin *behaviour* and the edges (empty market, stale
entry, slow subscriber, shutdown mid-flight), or just the happy path? Is the benchmark
measuring the hot path you actually care about (allocs/op, ns/op under read
contention)? Can you say what a CPU profile vs a heap/alloc profile would tell you and
which you'd open first for a latency regression?

---

## Done?

You should be able to hold a 30-minute conversation defending every decision:
representation, lock vs lock-free, clock injection, fan-out isolation, backpressure
policy, shutdown ownership, and what your benchmark proves. If any answer is "because
that's the default," go back and find the trade-off.
