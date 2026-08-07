# Bounded Blocking Queue

## Approach
The queue is backed by a fixed-length `Object[]` used as a circular buffer, tracked with two
cursors — `head` (next read position) and `tail` (next write position) — both advancing modulo
`capacity`, plus a `size` counter. A circular array gives O(1) enqueue/dequeue with none of the
allocation churn or pointer-chasing of a linked-node design, and keeps everything in one cache-
friendly block.

All state is guarded by a single `ReentrantLock`, but two separate `Condition`s are derived from
it: `notFull` (producers wait here when the queue is full) and `notEmpty` (consumers wait here when
it's empty). A successful `put()` calls `notEmpty.signal()` to wake exactly one consumer; a
successful `take()` calls `notFull.signal()` to wake exactly one producer. Splitting the conditions
means a producer never wastes a wakeup on another producer (and vice versa) — with a single shared
condition you'd be forced to `signalAll()` and let the wrong-party waiters immediately re-block.

Every wait is a `while` loop re-checking the predicate, not an `if`. `Condition.await()` can return
without a matching signal (a spurious wakeup, permitted by the JVM spec), and even on a real signal
another thread may have already consumed the freed slot/item before the woken thread gets
scheduled. Only re-checking the condition after waking is correct. The lock is always released in a
`finally` block so an exception mid-critical-section can't leave the queue permanently deadlocked.

This is a teaching re-implementation of `java.util.concurrent.ArrayBlockingQueue` — same core
mechanics, minus its fairness option and battle-tested edge-case handling.

## The real challenge
- **One lock, two conditions**: you must use `notFull` and `notEmpty` as separate conditions derived from the same lock. With a single shared condition you would have to call `signalAll()` (wasting work); two conditions let you call `signal()` on exactly the right waiters — one item added means exactly one consumer can proceed.
- **`while`, not `if`**: `Condition.await()` can return spuriously (no corresponding signal), and another thread may consume the freed slot before the woken thread is scheduled. Only a `while` loop re-checking the predicate is correct.
- **Unlock in `finally`**: any exception inside the critical section must not leave the lock held or the queue becomes permanently unusable.
- **Circular-array mechanics**: head/tail advance modulo capacity; size tracks the count; take must null out the vacated slot.

## Common mistakes & senior signal
- **Using `if` instead of `while` around the wait**: passes casual testing but is a race under real contention — the tell of someone who hasn't internalized spurious wakeups.
- **One condition instead of two**: works correctly but forces `signalAll()`, which is a measurable throughput regression under contention — senior candidates name this trade-off unprompted.
- **Forgetting to null out the vacated slot in `take()`**: silently retains a reference to every dequeued object, defeating GC for long-lived queues holding large payloads.
- **Not releasing the lock in `finally`**: an exception thrown mid-`put`/`take` leaves the lock held forever, wedging every other thread — a strong answer treats lock release as non-negotiable, independent of the happy path.
- **Reaching for `synchronized`/`wait`/`notify` instead of `Lock`/`Condition`**: works, but loses the ability to have two independent wait-sets, which is the whole point of the "signal exactly the right party" optimisation.
- **Checking interruption status instead of using `lockInterruptibly()`**: a subtle miss — without it, a thread blocked trying to *acquire* the lock (not just waiting on a condition) can't be interrupted at all.

## Extensions
- Add a `poll(timeout, TimeUnit)` / `offer(element, timeout, TimeUnit)` variant using `Condition.awaitNanos`.
- Add fairness (FIFO wake order) via `new ReentrantLock(true)`.
- Compare against a two-lock (Michael–Scott style) design and discuss why a single lock is simpler and sufficient here.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/blockingqueue/`)
- Java Interview Primer: Q41 (wait/sleep), Q39 (synchronized), Q47 (latch/barrier), Q255 (producer/consumer)
