# Lease

> Build a resource-lending pool whose leases return themselves — the pattern behind try-with-resources on a JDBC `Connection` or a `Closeable` buffered stream.

## The problem
Implement a single-threaded pool that lends out a fixed number of resources, one lease at a time. A lease is the caller's handle on the resource; returning it — normally by letting a try-with-resources block close it — makes the resource available to the next caller. The pool must never lose track of a resource, even when the caller's code throws, and returning a lease more than once must not corrupt the pool's accounting.

## Requirements
- Constructing the pool takes a resource factory and a fixed size; resources are created lazily, not all of them up front.
- Acquiring a resource hands out a lease; when all resources are already leased, acquiring fails with `IllegalStateException` instead of blocking or waiting (this pool is single-threaded — no blocking, no timeout).
- Checking availability reports how many resources are currently idle and could be leased right now.
- Getting the resource from a lease returns the thing being leased. Releasing a lease returns the resource to the pool so it can be leased again.
- Releasing a lease is **idempotent**: releasing an already-released lease is a no-op — it must not return the resource to the pool a second time or inflate the available count.

## What you're given
- `FaultyResource` — an `AutoCloseable` fixture whose `close()` always throws; use it to exercise suppressed exceptions. Copy it verbatim.

You design the entire public API — method names, parameters, return types — and the internals from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/lease/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
