# Scatter-Gather Aggregator

## Approach
`ScatterGather` holds a single injected `Executor`. The default no-arg constructor wires up
`Executors.newVirtualThreadPerTaskExecutor()` — virtual threads are cheap enough that no pool
sizing is needed, which is the right default for I/O-bound fan-out. Tests instead inject a
controlled `Executor` so they can assert on parallelism and lifecycle.

Both gather methods follow the same three-phase shape:
1. **Scatter** — submit every `Supplier<T>` to the executor via `CompletableFuture.supplyAsync`,
   keeping the list of individual futures. Accepting `Supplier<T>` rather than a pre-built
   `CompletableFuture<T>` keeps the tasks lazy: they only start once `supplyAsync` runs them on
   *our* executor, not immediately on the caller's thread.
2. **Barrier** — `CompletableFuture.allOf(...)` waits for every future to complete, but it
   returns `CompletableFuture<Void>`; it is a synchronization signal only, not a result carrier.
3. **Gather** — once the barrier fires, every underlying future is guaranteed done, so calling
   `.join()` on each one is safe (non-blocking, and rethrows synchronously if that task failed).

`gatherAll` gathers by calling `.join()` on each future in order inside `barrier.thenApply(...)`;
if any task failed, that `join()` rethrows and the returned future completes exceptionally —
fail-fast semantics, with no cancellation of the other in-flight tasks.

`gatherAllWithTimeout` wraps each raw future with `.orTimeout(timeoutNanos, NANOSECONDS)` so a
slow task fails exceptionally at the deadline, then chains `.handle((v, ex) -> ex == null ?
Optional.of(v) : Optional.empty())` to absorb both timeouts and task failures into an `Optional`.
That absorption is what keeps `allOf` itself from ever failing here — the barrier only needs to
wait, not propagate errors. The gather step then unwraps only the present `Optional`s, silently
dropping whatever timed out or failed. `orTimeout` (exceptional-on-timeout) is preferred over
`completeOnTimeout(default, ...)` because there's no meaningful sentinel value to invent for an
arbitrary `T`.

## The real challenge
- **`allOf` returns `Void`**: `CompletableFuture.allOf(...)` gives you a barrier that fires when all futures complete, but it holds no results. You must keep a reference to the original individual futures and collect their results after the barrier — a common interview stumbling block.
- **`gatherAllWithTimeout` partial results**: wrap each future with `.orTimeout(...)`, then chain `.handle((v, ex) -> ex == null ? Optional.of(v) : Optional.empty())`. The `handle` absorbs exceptions so `allOf` itself never fails; after the barrier you filter for `Optional.isPresent()`. Using `orTimeout` (exceptional on timeout) rather than `completeOnTimeout` (sentinel value) avoids inventing a meaningful default.
- **Keeping tasks lazy**: accept `Supplier<T>`, not pre-built `CompletableFuture<T>`. Pre-built futures start immediately on the caller's thread; suppliers start on your executor when you call `supplyAsync`.
- **Executor injection**: tests inject a controlled executor to verify parallelism and lifecycle. The production default of virtual threads requires no pool sizing.

## Common mistakes & senior signal
- **Forgetting `allOf` discards results** — chaining `allOf(...).thenApply(v -> v)` and trying to
  return `v` directly is a dead end; `v` is `Void`. A candidate who reaches for `.join()` on the
  original future list, not the barrier, shows they understand what the barrier actually
  synchronizes on.
- **Letting `orTimeout` failures propagate into `allOf`** — without the `.handle(...)` absorption
  step, a single slow task makes the whole `allOf` barrier fail exceptionally, defeating the
  "partial results" requirement. Strong candidates convert per-future failure into a value
  (`Optional`) *before* the barrier, not after.
- **Blocking the calling thread** — calling `.get()`/`.join()` per task inside the scatter loop
  (instead of `supplyAsync` + a barrier) serializes the fan-out and loses all concurrency benefit.
- **Cancelling on first failure in `gatherAll`** — it's tempting to cancel sibling futures the
  moment one throws; the reference implementation deliberately lets others keep running (simpler,
  and short-lived fan-out tasks rarely benefit from cancellation) — worth calling out as a
  conscious trade-off rather than an oversight.
- **Picking a sentinel default for `completeOnTimeout`** — using `completeOnTimeout(null, ...)` or
  some other placeholder instead of `orTimeout` + `Optional` conflates "no result" with "a real
  result", which breaks generically for an arbitrary `T`.

## Extensions
- Add a `gatherAny` that races tasks with `CompletableFuture.anyOf` and returns the first success —
  useful for hedged requests against redundant replicas.
- Support cancellation of in-flight tasks once `gatherAll` fails fast, instead of letting siblings
  run to completion.
- Add per-task tracing/metrics (submit time, completion time, success/timeout/failure) surfaced
  alongside the gathered results.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/aggregator/`)
- Java Interview Primer: Q50 (CompletableFuture), Q168 (CF exception handling), Q243 (scatter-gather)
