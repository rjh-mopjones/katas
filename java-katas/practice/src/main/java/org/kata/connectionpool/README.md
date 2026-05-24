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
The signatures, fields, constructors, and Javadoc are already in place — fill in the logic for:
- `ConnectionPool` — `borrow(long, TimeUnit)` and `release(R)`

(The `Semaphore`, `ConcurrentLinkedQueue<R> idle`, `AtomicInteger totalCreated`, `available()`, and `inUse()` are provided as scaffolding.)

## The real challenge
- **Semaphore as the bound**: the semaphore models "slots available" cleanly — `tryAcquire(timeout, unit)` blocks until a slot opens or the deadline elapses. Without it, you would have to manually count in-flight borrows and coordinate that count with the idle queue.
- **Release order matters**: in `release`, add the resource to the idle queue _before_ releasing the semaphore permit. If you release the permit first, another thread can acquire it and find the idle queue empty, causing it to create an extra resource beyond `maxSize`.
- **Validate-on-borrow loop**: after acquiring a permit, you may pull an invalid idle resource; you must discard it and try again (or create fresh), all while holding exactly one permit.
- **Lazy creation vs idle-queue empty**: an empty idle queue after a successful `acquire` means create a new resource only if `totalCreated < maxSize` — the semaphore's available permits encode this invariant.

## Run
```
mvn -pl practice test -Dtest=ConnectionPoolTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/connectionpool/`
- Java Interview Primer: Q191 (HikariCP sizing), Q48 (semaphores)
