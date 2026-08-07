# Exponential Backoff Retryer

> Implement retry with exponential backoff, a configurable cap, and full jitter to prevent thundering herds.

## The problem
Network calls and service requests fail transiently. Build a `Retryer` that executes a `Callable`, retries on any exception up to a configured limit, and waits between attempts according to an exponential backoff formula. The delay must be jittered to de-correlate retries across concurrent callers. Both the sleep mechanism and the random source must be injectable for deterministic testing.

## Requirements
- Attempt the action up to `policy.maxAttempts()` times (inclusive of the first call).
- After each failure that is not the last attempt, compute a base delay: `min(maxDelayMs, baseDelayMs × multiplier^(attempt-1))` where attempt is 1-indexed.
- When `policy.jitter()` is `true`, replace the computed delay with a uniform sample from `[0, computedDelay]` (full jitter).
- Do not sleep after the final failed attempt — throw the last exception immediately.
- Re-throw the last exception as-is (no wrapping) so the caller receives the original cause.
- Return the result of the first successful attempt without retrying further.
- The sleeper (`LongConsumer`) and random source (`Random`) are constructor-injected. Tests use a recording sleeper and a seeded `Random` to assert delay values deterministically.
- `RetryPolicy.computeDelayMs(attempt)` is the method you implement on the record.

## What you implement
Implement `Retryer` from scratch — the public API is two constructors and `execute(Callable<T>)`. You design the internal fields and retry logic yourself.

Also implement `RetryPolicy.computeDelayMs(int attempt)` — the record components, compact constructor validation, and `noRetry()` factory are provided and working.

(`RetryPolicy` record structure is provided as a working fixture.)

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/retry/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
