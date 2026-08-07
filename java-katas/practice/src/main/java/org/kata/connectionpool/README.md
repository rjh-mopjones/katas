# Connection Pool

> Build a generic, bounded, thread-safe resource pool — the primitive underlying HikariCP and every production JDBC pool.

## The problem
Implement a generic pool that manages a fixed maximum number of reusable resources (connections, clients, file handles). Callers borrow a resource, use it, and return it. If all resources are in use, a caller must block up to a timeout before receiving `null`. Resources that fail validation on borrow must be discarded and replaced transparently.

## Requirements
- `borrow(long timeout, TimeUnit unit)` returns a resource if one becomes available within the timeout, or `null` on timeout. Throws `InterruptedException` if interrupted.
- `release(R resource)` returns the resource to the pool; must not accept null.
- The number of resources concurrently in use must never exceed `maxSize`.
- Resources are created lazily (on first borrow, not at construction time).
- Before returning an idle resource, validate it with the provided `Predicate<R>`; if validation fails, discard it and create a fresh one instead.
- `available()` reports idle count; `inUse()` reports borrowed count. Both are point-in-time snapshots.
- Constructors: one with factory + maxSize (always-valid), one with factory + maxSize + validator.

## What you implement
Implement `ConnectionPool<R>` from scratch — the public API is two constructors (`factory + maxSize`, and `factory + maxSize + validator`), `borrow(long, TimeUnit)`, `release(R)`, `available()`, and `inUse()`. You design the internal resource tracking, bounding mechanism, and validation loop yourself.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/connectionpool/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
