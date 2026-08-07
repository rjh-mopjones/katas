# Lease

> Build a resource-lending pool whose leases return themselves — the pattern behind try-with-resources on a JDBC `Connection` or a `Closeable` buffered stream.

## The problem
Implement a single-threaded pool that lends out a fixed number of resources, one `Lease` at a time. A lease is the caller's handle on the resource; returning it — normally by letting a try-with-resources block close it — makes the resource available to the next caller. The pool must never lose track of a resource, even when the caller's code throws, and closing a lease more than once must not corrupt the pool's accounting.

## Requirements
- `Pool(Supplier<R> factory, int size)` — resources are created lazily, not all `size` of them up front.
- `Pool.acquire()` hands out a `Lease<R>`; throws `IllegalStateException` when all `size` resources are already leased (single-threaded — no blocking, no timeout).
- `Pool.available()` reports how many resources are currently idle and could be leased right now.
- `Lease<R>` implements `AutoCloseable`. `get()` returns the leased resource. `close()` returns the resource to the pool.
- `Lease.close()` is **idempotent**: closing an already-closed lease is a no-op — it must not return the resource to the pool a second time or inflate `available()`.
- `FaultyResource` (provided, copy verbatim) is an `AutoCloseable` whose `close()` always throws — use it to exercise suppressed exceptions.

## What you implement
Implement `Pool<R>` and `Lease<R>` from scratch — the public API is `Pool`'s constructor, `acquire()`, `available()`, and `Lease`'s `get()` and `close()`. You design how a `Lease` finds its way back to its `Pool`, how idle resources are tracked and reused, and how the idempotent-close guard works.

## The real challenge
- **Deterministic cleanup, not "remember to call close()".** A caller's code between acquire and close can throw. The resource must still come back to the pool — that's the entire reason try-with-resources exists, and it's what your tests should prove, not just assume.
- **Idempotent close.** Nested or defensive cleanup code routinely calls `close()` more than once; `AutoCloseable`'s contract expects a second call to be harmless. Get this wrong and two callers can end up believing they each hold the same "exclusive" resource.
- **Suppressed exceptions.** When a try-with-resources body throws and a resource's `close()` *also* throws while unwinding, the JVM doesn't discard either — the close-time exception is attached to the body's exception via `addSuppressed`. `FaultyResource` exists so you can watch this happen and read it back off `getSuppressed()`.
- **Reuse, not just recycling the count.** A resource returned to the pool should be handed out again on a later `acquire()`, not silently dropped in favour of always calling the factory — otherwise "pooling" is just an expensive way to count.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/lease/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.

## Reference
- Worked solution: `solution/src/main/java/org/kata/lease/`
- Java Interview Primer: try-with-resources / `AutoCloseable` / suppressed exceptions
