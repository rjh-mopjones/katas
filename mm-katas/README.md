# mm-katas — market-making live-coding drills

Java katas built for one specific ordeal: a **90-minute live-coding round at a sports-betting
market-making firm**, where the interviewer starts you on one problem and bolts on a new requirement
every ~20 minutes. Each kata escalates over **four stages**: Stage 1 is the naive single-threaded
version; later stages add requirements that **break the obvious Stage 1 design** and force a pivot.
That pivot — under time pressure, on your own tests — is the thing you're practising.

Not LeetCode. These are the streaming/state/concurrency and betting-maths problems this desk actually
asks.

## Quickstart

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # Java 21 required
./kata 01            # start kata 01: prints Stage 1 only, starts a stopwatch
#  … write your tests, implement, then:
./kata 01 check      # runs the current stage; on green, unlocks + reveals the next stage
./kata               # list all katas
./kata 01 reset      # clear progress, re-lock later stages
```

Each kata is one package `com.katas.k<NN>_<slug>`. You implement the `Starter`-style class; the domain
types, the per-stage `README.md`, and the tests are given. Stage 2+ tests are `@Disabled` and revealed
only as you pass the current stage — **don't read ahead**. Write your own tests too; the shipped ones
pin the contract.

Worked references live **outside** the source root in `solutions/` (a full reference + a `NOTES.md`
walking the pivot each stage forces). Prove a reference passes every stage:

```bash
solutions/verify.sh 01
```

## The katas

| # | Kata | Difficulty | Drills |
|---|------|-----------|--------|
| 01 | [Limit order book — price-time priority](src/main/java/com/katas/k01_orderbook/) | hard | matching, partial fills, market orders + STP, concurrency · **ready** |
| 02 | Odds feed parser | hard | line protocol, partial/malformed chunks, per-book seq + gaps, backpressure · **ready** |
| 03 | Sliding window aggregator | medium-hard | rolling count/sum, weighted avg, out-of-order + lateness, memory bound · **ready** |
| 04 | Idempotent event processor | medium-hard | dedup, bounded-memory expiry, at-least-once redelivery, per-key ordering · _planned_ |
| 05 | Position & exposure keeper | medium-hard | net position, realised/unrealised P&L, correlated markets, concurrent readers · _planned_ |
| 06 | Top-K over a stream | medium-hard | top-K by count, decaying counts, updates/removals, approximate under a cap · _planned_ |
| 07 | Quote-expiry cache | medium | TTL on read, active eviction (DelayQueue), per-entry TTL, lock-free safety · _planned_ |
| 08 | Odds converter | medium | decimal/fractional/American/implied, rounding, BigDecimal vs double · _planned_ |
| 09 | Overround & de-vig calculator | medium | margin, proportional/other de-vig, multi-way + dead heats · _planned_ |
| 10 | Cross-book arbitrage detector | hard | best-per-outcome, arb detection, stake allocation, live stream + stale filter · _planned_ |
| 11 | Bounded blocking queue | medium | hand-rolled wait/notify then `Condition` · _planned_ |
| 12 | Scatter-gather across N venues | medium | per-call timeouts, partial results · _planned_ |

Katas 1–3 are complete. 4–12 land after the format is signed off — see `HOWTO.md`.

## Rules of engagement

- Maven, Java 21, JUnit 5, AssertJ. No Spring, no Guava.
- Reference solutions prefer `java.util.concurrent` primitives; each kata's `INTERVIEWER.md` notes where
  a **hand-rolled** version (wait/notify, a lock-free ring) would be the follow-up ask.
- Treat every stage as the interviewer's next sentence. Set a timer. Talk out loud. Write the test first.
