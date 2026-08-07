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

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/lease/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
