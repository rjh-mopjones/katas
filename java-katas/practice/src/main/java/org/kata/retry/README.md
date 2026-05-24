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

## The real challenge
- **Thundering-herd reasoning**: without jitter, all callers that fail at the same instant retry at the same instants, turning a transient outage into a sustained overload spike. Full jitter (`random(0, delay)`) maximally spreads retries across the delay window. Be able to explain this trade-off and name the three jitter strategies (full, equal, decorrelated).
- **No sleep on last attempt**: computing a delay and sleeping only to immediately throw is wasted time. The check `if (attempt == maxAttempts) break` must happen before the sleep call.
- **Cap before jitter**: `maxDelayMs` is applied to the raw exponential value; jitter is then applied to the capped value. Applying jitter before the cap could produce delays that inadvertently exceed `maxDelayMs`.
- **Idempotency contract**: retrying a non-idempotent operation (e.g., charging a credit card) causes duplicate side-effects. A production retryer would accept a `Predicate<Exception>` to classify which exceptions are retryable — know this limitation even though this kata retries on any exception.
- **Injectable sleeper**: `Thread.sleep` in production; a `List`-collecting `LongConsumer` in tests. This lets tests assert the exact delay sequence without the test suite taking real wall-clock time.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/retry/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/retry/`
- Java Interview Primer: Q235 (retry with backoff + jitter), resilience patterns
