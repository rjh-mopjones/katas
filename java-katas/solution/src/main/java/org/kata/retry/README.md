# Exponential Backoff Retryer

## Approach
- **Single retry loop, attempts 1-indexed.** `execute` runs a `for` loop from `1` to
  `policy.maxAttempts()`. Each iteration calls `action.call()` inside a `try`; on success it returns
  immediately, on failure it records the exception as `lastException` and decides whether to sleep and
  loop or fall through and re-throw.
- **Delay computation lives on the policy.** `RetryPolicy` is an immutable `record`, which gives value
  semantics and thread-safety for free — one policy can be shared across many `Retryer` instances with
  no risk of mid-flight mutation. `computeDelayMs(attempt)` applies the backoff formula
  `min(maxDelayMs, baseDelayMs × multiplier^(attempt-1))` using `Math.pow` (fine here — this is not a
  hot loop) and caps with `Math.min` before returning.
- **Cap first, then jitter.** The raw exponential is capped at `maxDelayMs` inside `computeDelayMs`;
  `execute` then applies full jitter to the already-capped value by sampling
  `(long)(random.nextDouble() * (delayMs + 1))`, keeping the sampled delay within `[0, delayMs]`.
- **Break before the final sleep.** The `if (attempt == maxAttempts) break` check runs *before* the
  delay/sleep, so no time is wasted waiting after the last failed attempt — the loop falls through and
  `throw lastException` re-throws the original cause unwrapped.
- **Everything time- and randomness-related is injected.** The `LongConsumer sleeper` (default
  `Thread.sleep`) and `Random` (default unseeded) are constructor parameters so tests inject a
  recording sleeper and a seeded `Random` to assert the exact delay sequence with no real wall-clock
  time.

## The real challenge
- **Thundering-herd reasoning**: without jitter, all callers that fail at the same instant retry at the same instants, turning a transient outage into a sustained overload spike. Full jitter (`random(0, delay)`) maximally spreads retries across the delay window. Be able to explain this trade-off and name the three jitter strategies (full, equal, decorrelated).
- **No sleep on last attempt**: computing a delay and sleeping only to immediately throw is wasted time. The check `if (attempt == maxAttempts) break` must happen before the sleep call.
- **Cap before jitter**: `maxDelayMs` is applied to the raw exponential value; jitter is then applied to the capped value. Applying jitter before the cap could produce delays that inadvertently exceed `maxDelayMs`.
- **Idempotency contract**: retrying a non-idempotent operation (e.g., charging a credit card) causes duplicate side-effects. A production retryer would accept a `Predicate<Exception>` to classify which exceptions are retryable — know this limitation even though this kata retries on any exception.
- **Injectable sleeper**: `Thread.sleep` in production; a `List`-collecting `LongConsumer` in tests. This lets tests assert the exact delay sequence without the test suite taking real wall-clock time.

## Common mistakes & senior signal
- **Sleeping after the last failure.** The most common bug: computing and sleeping a delay on the final
  attempt before throwing. A strong answer breaks out of the loop *before* the sleep and gets the
  exception back to the caller as fast as possible.
- **Applying jitter before the cap.** Jittering the raw exponential and then capping (or forgetting to
  cap) lets a single delay exceed `maxDelayMs`. Cap in `computeDelayMs`, jitter the capped value.
- **Wrapping the exception.** Re-throwing inside a `RuntimeException` hides the original cause. The
  contract is to re-throw the last exception *as-is*.
- **Off-by-one on the attempt index / count.** `maxAttempts` is inclusive of the first call, and the
  formula is 1-indexed (`attempt 1 → base × m^0 = base`). Mixing up "attempts" vs "retries" is an easy
  slip.
- **Retrying blindly on any exception.** This kata retries on everything for simplicity, but the senior
  signal is naming the limitation: a `400 Bad Request` should not be retried, a `503` should — real
  systems take a `Predicate<Exception>` to classify retryable failures, and retries are only safe for
  idempotent operations.
- **Hard-coding `Thread.sleep` / `new Random()`.** Non-injectable time and randomness make the code
  untestable without real sleeps and flaky assertions. Injecting both is what lets tests be
  deterministic and fast.

## Extensions
- Accept a `Predicate<Exception>` so callers classify which exceptions are retryable (retry `503`, fail
  fast on `400`).
- Offer the other jitter strategies — equal jitter (`delay/2 + random(0, delay/2)`) and decorrelated
  jitter (`random(base, lastSleep × 3)`) — behind the policy.
- Replace the fixed `maxAttempts` count with a deadline/latency budget, since a large attempt count
  dramatically inflates tail latency.
- Track the previous sleep to support decorrelated jitter, which spreads load more evenly for large
  retry counts.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/retry/`)
- Java Interview Primer: Q235 (retry with backoff + jitter), resilience patterns
