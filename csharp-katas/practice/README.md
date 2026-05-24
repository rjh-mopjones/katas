# Practice

Implement each kata from scratch. The classes you must build are here as **blank skeletons** —
public signatures whose bodies `throw new NotImplementedException()`. Supporting fixtures
(interfaces, records, enums) are real so the project compiles. Each kata folder has a `README.md`
stating the problem.

There are **no tests here** — writing your own is part of the exercise.

## Loop

1. Read the kata's `README.md` (e.g. `Cache/README.md`).
2. Implement the skeleton class(es) in `practice/<Kata>/`.
3. Write your own tests under `practice.tests/<Kata>/` (xUnit is already wired up).
4. Run them:
   ```bash
   dotnet test practice.tests --filter "FullyQualifiedName~<Kata>"
   ```
5. Stuck? Open the twin under `../solution/<Kata>/` — full implementation with commentary — and the
   reference tests under `../solution.tests/<Kata>/`.

## Side-by-side with the solution

Every practice file has a twin at the same relative path under `../solution/`:

```
practice/Cache/LruCache.cs    <- you implement
solution/Cache/LruCache.cs    <- the worked solution
```

## Suggested order (easier → harder)

1. `Records`, `Nullable` — warm-up on the type system and null-safety.
2. `CustomLinq`, `Iterators`, `PatternMatching`, `ValueEquality`, `Structs` — core language features.
3. `Generics`, `EventsDelegates`, `Disposables` — abstractions and lifecycle.
4. `AsyncStreams`, `Retry`, `Idempotency` — async fundamentals.
5. `Channels`, `ScatterGather`, `ConcurrencyPrimitives`, `RateLimit`, `CircuitBreaker`, `Scheduler`, `Cache` — concurrency & resilience.
6. `LockFreeStack`, `ExpressionTrees` — the hard ones.

> **Time-dependent katas** (Retry, RateLimit, CircuitBreaker, Scheduler) take a `TimeProvider` —
> inject `Microsoft.Extensions.Time.Testing.FakeTimeProvider` in your tests and call
> `fake.Advance(...)` instead of sleeping.
