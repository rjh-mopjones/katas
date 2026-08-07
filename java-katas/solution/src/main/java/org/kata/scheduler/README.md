# Task Scheduler

## Approach
The scheduler is a single-worker consumer over a `java.util.concurrent.DelayQueue<ScheduledTask>`.
`ScheduledTask` implements `Delayed`, storing an absolute due time as a `System.nanoTime()` value
rather than a wall-clock timestamp — `nanoTime` is monotonic, so it is immune to system clock
adjustments and safe for elapsed-time math. `getDelay(TimeUnit)` returns `dueNanos - nanoTime()`
converted to the requested unit; `compareTo` orders tasks by `dueNanos` using `Long.compare` (a
naive `(int) (a - b)` subtraction would overflow once the gap exceeds `Integer.MAX_VALUE`).

`DelayQueue` is chosen specifically because `take()` parks the calling thread with zero CPU cost
until the head element's delay reaches zero — no polling, no `Thread.sleep` loop. Internally it is
a priority heap ordered by `compareTo`, so the worker always wakes for the soonest-due task, and a
newly inserted task with a sooner due time causes the queue to re-signal the waiting thread so it
re-examines the new head. This is the same primitive `ScheduledThreadPoolExecutor` builds on
(its `DelayedWorkQueue`), just without the thread pool, `scheduleAtFixedRate`, and shutdown-drain
semantics layered on top.

`start()`/`close()` follow the idiomatic "thread-owning resource" pattern: `start()` spins up a
single (virtual) worker thread; `close()` flips a `volatile running` flag and interrupts the
worker, which unblocks `queue.take()` with an `InterruptedException` the loop catches, restoring
the interrupt flag and exiting. `close()` joins the worker with a 1-second timeout so callers
don't hang.

Cancellation is a simple `AtomicBoolean` flag on `ScheduledTask`, checked by the worker after
`take()` returns. Cancelled tasks are not re-queued or removed from the heap eagerly — they are
simply skipped when they surface, which is the standard trade-off for a "cancel-before-run" queue.

## The real challenge
- **`DelayQueue.take()` as a zero-CPU wait**: `take()` parks the worker thread until the head element's `getDelay()` returns ≤ 0. This is categorically different from a `Thread.sleep` loop — the OS wakes the thread at exactly the right time. A newly added task with a sooner due time causes the queue to unpark the waiting thread so it can re-examine the new head.
- **`getDelay` must use `System.nanoTime()`**: `dueNanos` is stored as a `nanoTime` value; comparing it against `currentTimeMillis` would corrupt the delay calculation. Monotonic time is also immune to system clock adjustments.
- **`compareTo` with `Long.compare`**: naive `(int)(this.dueNanos - that.dueNanos)` overflows when the difference exceeds `Integer.MAX_VALUE`. Use `Long.compare`.
- **Shutdown via interrupt**: `close()` sets `running = false` and interrupts the worker. `DelayQueue.take()` throws `InterruptedException` on interrupt; the worker loop catches it, restores the interrupt flag, and exits. This is the idiomatic pattern for a thread that owns a blocking queue.
- **Cancellation check after take**: the worker must check `task.isCancelled()` after `take()` returns and skip the action if cancelled. Cancelled tasks evaporate — they are never re-queued.

## Common mistakes & senior signal
- **Busy-waiting or polling**: reaching for a `Thread.sleep(x)` loop instead of `DelayQueue.take()` burns CPU and adds latency jitter. A strong answer identifies `take()`'s blocking semantics as the whole point of the exercise.
- **Mixing clock sources**: computing the due time with `System.currentTimeMillis()` but comparing with `nanoTime()` (or vice versa) silently corrupts every delay calculation. Senior candidates default to `nanoTime()` for elapsed-time math and explain why.
- **Integer overflow in `compareTo`**: subtracting two `long` due-times and narrowing to `int` looks fine in tests but breaks once the gap exceeds ~2.1 seconds in nanoseconds. Using `Long.compare` (or `Long.signum` of the difference) is the tell.
- **Swallowing `InterruptedException` incorrectly**: catching it and continuing the loop instead of restoring the interrupt flag and exiting leaves the scheduler in a zombie state after `close()`. Restoring the flag (`Thread.currentThread().interrupt()`) before breaking is the idiomatic pattern.
- **Letting one bad task kill the worker**: not wrapping `action.run()` in its own try/catch means a single throwing task takes down the whole worker thread silently. Isolating task exceptions is a production-readiness signal.
- **Confusing cancel-before-run with interrupting a running task**: `cancel()` only prevents a not-yet-started task from running; it cannot stop a task already executing. Candidates who conflate the two overpromise the API's guarantees.

## Extensions
- **Thread pool for parallelism**: swap the single worker for a pool that pulls from the same `DelayQueue`, letting long-running tasks execute concurrently instead of serializing behind the queue.
- **Recurring tasks (`scheduleAtFixedRate` / `scheduleWithFixedDelay`)**: build fixed-rate scheduling by having a task reschedule itself at the end of its own execution: `schedule(() -> { action.run(); reschedule(...); }, period, unit)`. Fixed-rate reschedules relative to the *previous start*; fixed-delay reschedules relative to the *previous end*.
- **Draining pending tasks on shutdown**: `close()` currently discards queued-but-not-yet-due tasks. An alternative shutdown mode could drain and either run or explicitly cancel-and-report them instead of silently dropping them.

## Reference
- Worked solution: this package (`solution/src/main/java/org/kata/scheduler/`)
- Java Interview Primer: Q174 (scheduleAtFixedRate vs withFixedDelay), Q255 (DelayQueue/producer-consumer)
