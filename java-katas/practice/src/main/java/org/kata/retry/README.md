# Exponential Backoff Retryer

> Implement retry with exponential backoff, a configurable cap, and full jitter to prevent thundering herds.

## The problem
Network calls and service requests fail transiently. Build a retryer that executes an action, retries on any exception up to a configured limit, and waits between attempts according to an exponential backoff formula. The delay must be jittered to de-correlate retries across concurrent callers. Both the sleep mechanism and the random source must be injectable for deterministic testing.

## Requirements
- Attempt the action up to the configured maximum number of attempts (inclusive of the first call).
- After each failure that isn't the last attempt, compute a base delay: `min(maxDelayMs, baseDelayMs × multiplier^(attempt-1))`, where attempt is 1-indexed.
- When jitter is enabled, replace the computed delay with a uniform random sample from `[0, computedDelay]` (full jitter).
- Do not delay after the final failed attempt — the last exception propagates immediately.
- The exception that ends the final attempt propagates as-is (no wrapping), so the caller sees the original cause.
- The result of the first successful attempt is returned without retrying further.
- The delay mechanism and the random source used for jitter are both injectable, so tests can assert delay values deterministically instead of sleeping for real.

## What you're given
- `RetryPolicy` — the immutable backoff configuration (record; validates its own fields and provides a no-retry factory). Its delay-computation formula is left for you to implement.

You implement the retryer itself from scratch — its construction and its retry-loop execution — plus `RetryPolicy`'s delay computation.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/retry/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
