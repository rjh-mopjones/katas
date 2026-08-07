# Lease

## Approach
- `Pool<R>` holds an `ArrayDeque<R>` of idle resources plus a plain `int leased` counter, with the
  invariant `available() == size - leased` held at all times.
- `acquire()` reuses an idle resource if one is waiting (`idle.pop()`); only if none are idle does
  it call `factory.get()`. Resources are created lazily — the constructor never pre-warms all
  `size` of them — so construction cost is only paid when it's actually needed.
- Because this is single-threaded, `acquire()` on an exhausted pool throws `IllegalStateException`
  immediately rather than blocking: there is no other thread that could ever return a resource
  while the caller waits, so blocking would just deadlock.
- `release(R)` is package-private and called exactly once per lease, only from `Lease.close()`. It
  pushes the resource back onto the idle deque (LIFO reuse — keeps a small working set "hot") and
  decrements `leased`.
- `Lease<R>` holds the resource, a back-reference to its `Pool`, and a `boolean closed` flag.
  `close()` checks-and-sets that flag before calling `pool.release(...)`, which is what makes a
  second `close()` call a safe no-op. `close()` deliberately does *not* call `close()` on the
  underlying resource `R` — the pool owns the resource's lifecycle, not the lease; "closing a
  lease" means "I'm done borrowing," not "destroy this."

## The real challenge
- **Deterministic cleanup, not "remember to call close()".** A caller's code between acquire and close can throw. The resource must still come back to the pool — that's the entire reason try-with-resources exists, and it's what your tests should prove, not just assume.
- **Idempotent close.** Nested or defensive cleanup code routinely calls `close()` more than once; `AutoCloseable`'s contract expects a second call to be harmless. Get this wrong and two callers can end up believing they each hold the same "exclusive" resource.
- **Suppressed exceptions.** When a try-with-resources body throws and a resource's `close()` *also* throws while unwinding, the JVM doesn't discard either — the close-time exception is attached to the body's exception via `addSuppressed`. `FaultyResource` exists so you can watch this happen and read it back off `getSuppressed()`.
- **Reuse, not just recycling the count.** A resource returned to the pool should be handed out again on a later `acquire()`, not silently dropped in favour of always calling the factory — otherwise "pooling" is just an expensive way to count.

## Common mistakes & senior signal
- Returning the resource in the *caller's* try/finally rather than inside `Lease.close()` —
  defeats the point of `AutoCloseable`; the pool's return path should be self-enforcing through
  try-with-resources, not something every caller has to remember.
- Missing the idempotent-close guard — the first thing to check is calling `close()` twice and
  asserting `available()` didn't get inflated.
- Not writing a test around `FaultyResource` that asserts on `getSuppressed()` — most candidates
  don't realize the JVM attaches a close-time exception to the body's exception until it's asked
  for explicitly.
- Pre-warming the pool (creating all `size` resources in the constructor) — contradicts the lazy
  creation requirement and wastes setup cost on slots that may never be used.
- Not thinking about LIFO vs FIFO reuse as a deliberate choice — either is acceptable per the
  contract, but a senior answer explains the trade-off (LIFO keeps a small hot set; FIFO
  round-robins wear evenly) rather than picking one by accident.

## Extensions
- Blocking/timeout acquire across threads — the concurrent cousin swaps the deque + counter for a
  `Semaphore` plus a thread-safe idle queue.
- LIFO vs FIFO reuse — this pool reuses most-recently-returned first (a stack), keeping a small
  working set hot; a FIFO queue would round-robin resources evenly instead.
- Validation on reuse — before handing back an idle resource, run it through a `Predicate<R>` and
  discard + recreate on failure.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/lease/`)
- Java Interview Primer: try-with-resources / `AutoCloseable` / suppressed exceptions
