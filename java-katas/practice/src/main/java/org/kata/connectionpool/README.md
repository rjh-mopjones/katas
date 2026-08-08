# Connection Pool

> Build a generic, bounded, thread-safe resource pool — the primitive underlying HikariCP and every production JDBC pool.

## The problem
Implement a generic pool that manages a fixed maximum number of reusable resources (connections,
clients, file handles). Callers borrow a resource, use it, and return it. If all resources are in
use, a caller must block up to a timeout before receiving nothing. Resources that fail validation
on borrow must be discarded and replaced transparently.

## Requirements
- Borrowing with a timeout returns a resource if one becomes available within that timeout, or
  nothing if the timeout elapses; it must propagate interruption to the caller.
- Returning a resource must not accept a null resource.
- The number of resources concurrently in use must never exceed the configured maximum size.
- Resources are created lazily (on first borrow, not at construction time).
- Before returning an idle resource, it must be validated; if validation fails, the resource is
  discarded and a fresh one created instead.
- The idle count and the in-use count must both be queryable as point-in-time snapshots.
- The pool must support construction with just a factory and a maximum size (always-valid
  resources), and separately with a factory, a maximum size, and a validator.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/connectionpool/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
