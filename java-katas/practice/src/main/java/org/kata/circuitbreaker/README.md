# Circuit Breaker

> Implement the circuit breaker resilience pattern with a three-state machine and time-based recovery.

## The problem
Services that depend on a downstream system (database, external API) must stop hammering it when
it starts failing. Build a circuit breaker that wraps arbitrary actions: after a threshold of
consecutive failures it opens and fast-rejects all calls; after a configured timeout it allows a
small number of trial calls through to probe recovery; if the trials succeed it closes again.

## Requirements
- Three states: closed (normal), open (fast-reject), half-open (trial probe).
- While closed: every failure increments a consecutive-failure counter; any success resets it to
  zero. When the counter reaches the configured failure threshold, transition to open.
- While open: invoking an action fails fast without invoking it. After the configured open
  duration elapses, transition to half-open.
- While half-open: calls are forwarded. Each success increments a trial counter; when it reaches
  the configured success threshold, transition to closed. The first failure immediately reopens
  the breaker and resets the open timer.
- The clock must be injectable and use monotonic time — never wall-clock time, which can jump
  backwards.
- The lock must not be held while invoking the wrapped action — actions can be slow (network
  calls) and holding the lock would serialise all callers.
- The current state must be queryable, refreshing any pending open-to-half-open transition before
  returning it.

## What you're given
`CircuitBreakerException` (thrown when the breaker is open) and the `CircuitState` enum
(`CLOSED`, `OPEN`, `HALF_OPEN`) are provided as fully working types.

You design the entire public API — method names, parameters, return types — and the internals
from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/circuitbreaker/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
