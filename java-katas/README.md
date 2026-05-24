# Java Katas

LLD / concurrency interview katas, organised as a two-module Maven project so you can practice
each kata next to its worked solution.

```
katas/
├── pom.xml              # parent (aggregates both modules)
├── solution/            # full reference implementations + tests  (always green)
│   └── src/{main,test}/java/org/kata/...
└── practice/            # blank SUT skeletons + per-kata READMEs — no tests provided
    └── src/main/java/org/kata/...
```

Same relative path in both modules → open the twins in a split editor to work side-by-side.
See [`practice/README.md`](practice/README.md) for the practice workflow.

## Build

Requires **JDK 21+** (the POM targets `release 21`). If your default `mvn` uses an older JDK
(e.g. Corretto 17), point `JAVA_HOME` at a 21 JDK first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

mvn -pl solution test    # reference suite — green
mvn -pl practice test    # runs the tests you write under practice/src/test
mvn test                 # everything
```

## Katas

| Package | What you build |
|---------|----------------|
| `bank` | account service, deadlock-free transfer (lock ordering) |
| `cinema` | concurrent seat booking with holds/expiry |
| `elevator` | elevator scheduling controller |
| `orderbook` | price-time matching engine |
| `parking` | parking lot allocation (spot hierarchy) |
| `restaurant` | table booking with time-slot overlap |
| `vending` | vending machine state machine + change-making |
| `cache` | LRU, LFU, concurrent LRU |
| `ratelimit` | token bucket, leaky bucket, sliding window |
| `circuitbreaker` | CLOSED/OPEN/HALF_OPEN state machine |
| `retry` | exponential backoff + jitter |
| `blockingqueue` | bounded blocking queue (lock + 2 conditions) |
| `connectionpool` | semaphore-bounded resource pool |
| `aggregator` | CompletableFuture scatter-gather |
| `eventbus` | synchronous pub/sub (Observer) |
| `idempotency` | exactly-once / dedup processor |
| `scheduler` | delay-queue task scheduler |
| `lockfree` | Treiber stack, Michael-Scott queue, ABA-safe stack |

> **IntelliJ:** after the restructure, re-import the Maven project (open `pom.xml` as project, or
> *Maven tool window → Reload*) so both modules are recognised.
