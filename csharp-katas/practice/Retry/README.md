# Retry

Implement exponential-backoff retry with full jitter and injectable time/randomness.

## The problem

Production code that calls external services needs retry logic with exponential backoff and jitter to avoid thundering-herd reconnection storms. You need to implement `RetryPolicy` (a record that computes clamped, optionally-jittered delays) and `Retrier` (an async executor that retries on failure, sleeps between attempts, and never retries genuine cancellations).

## Requirements

- `RetryPolicy` is a positional record with five parameters; add validation in property initialisers: `MaxAttempts >= 1`, `BaseDelay >= Zero`, `MaxDelay >= BaseDelay`, `Multiplier >= 1.0`.
- `RetryPolicy.ComputeDelay(attempt, jitterFraction)` — computes `BaseDelay * Multiplier^(attempt-1)`, clamps to `MaxDelay`, then if `UseJitter` multiplies by `jitterFraction`. Overflow-safe: compute in `double` ticks before converting.
- `Retrier(policy, timeProvider?, jitterSource?)` — stores the three dependencies; defaults `TimeProvider.System` and `Random.Shared.NextDouble`.
- `Retrier.ExecuteAsync<T>(action, ct)` — loops up to `MaxAttempts` times; on failure sleeps `ComputeDelay(attempt, jitterSource())` unless it's the last attempt; re-throws `OperationCanceledException` immediately without retrying; re-throws the last exception after exhaustion.

## What you implement

```csharp
// RetryPolicy (record)
public sealed record RetryPolicy(int MaxAttempts, TimeSpan BaseDelay, TimeSpan MaxDelay, double Multiplier, bool UseJitter)
public TimeSpan ComputeDelay(int attempt, double jitterFraction)

// Retrier
public Retrier(RetryPolicy policy, TimeProvider? timeProvider = null, Func<double>? jitterSource = null)
public Task<T> ExecuteAsync<T>(Func<CancellationToken, Task<T>> action, CancellationToken ct = default)
```

## The real challenge

Two subtle points: (1) C# positional records have no compact constructor, so validation must live in property initialisers that reference the primary constructor parameters by name. (2) `OperationCanceledException` must be caught separately and re-thrown immediately — if you catch it with the general `catch (Exception)` and retry it, you'll spin the retry loop on cancellations, which is both wrong and hard to spot in tests. Also, the delay must not be applied after the final failed attempt.

## Run

Write your own tests under `practice.tests/Retry/`, then:

```bash
dotnet test practice.tests --filter "FullyQualifiedName~Retry"
```

## Reference

- Worked solution + tests: `solution/Retry/` and `solution.tests/Retry/`
- Microsoft Docs: https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/builtin-types/record
