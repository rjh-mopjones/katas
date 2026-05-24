# Task Scheduler

> Build a one-shot delay scheduler using a DelayQueue — the primitive that drives ScheduledThreadPoolExecutor.

## The problem
Implement a `TaskScheduler` that accepts one-shot `Runnable` tasks with a delay and executes them on a single background worker thread when their time is due. The scheduler must order tasks by due time, sleep efficiently (no busy-waiting or polling loops), support pre-run cancellation, and shut down cleanly when closed.

## Requirements
- `start()` launches the worker thread; throws `IllegalStateException` if already started.
- `schedule(Runnable action, long delay, TimeUnit unit)` enqueues a task and returns a `ScheduledTask` handle. Throws if the scheduler is not running, action is null, delay is negative, or unit is null.
- `ScheduledTask.cancel()` marks the task cancelled; the worker skips it if cancellation happens before execution. Idempotent.
- Tasks execute in due-time order; a newly scheduled task with a sooner due time should preempt the current wait.
- The worker thread must not busy-wait — it must block until the next task is due.
- `close()` (AutoCloseable) interrupts the worker, waits up to 1 second for it to exit, and prevents further scheduling.
- If a task action throws, the exception is swallowed so the worker continues draining the queue.

## What you implement
Implement `TaskScheduler` from scratch — the public API is `start()`, `schedule(Runnable, long, TimeUnit)` (returns a `ScheduledTask`), and `close()`. You also implement the inner `ScheduledTask` type with `cancel()`, `isCancelled()`, `getDelay(TimeUnit)`, and `compareTo(Delayed)`. You design the worker thread, `DelayQueue` usage, and shutdown logic yourself.

## The real challenge
- **`DelayQueue.take()` as a zero-CPU wait**: `take()` parks the worker thread until the head element's `getDelay()` returns ≤ 0. This is categorically different from a `Thread.sleep` loop — the OS wakes the thread at exactly the right time. A newly added task with a sooner due time causes the queue to unpark the waiting thread so it can re-examine the new head.
- **`getDelay` must use `System.nanoTime()`**: `dueNanos` is stored as a `nanoTime` value; comparing it against `currentTimeMillis` would corrupt the delay calculation. Monotonic time is also immune to system clock adjustments.
- **`compareTo` with `Long.compare`**: naive `(int)(this.dueNanos - that.dueNanos)` overflows when the difference exceeds `Integer.MAX_VALUE`. Use `Long.compare`.
- **Shutdown via interrupt**: `close()` sets `running = false` and interrupts the worker. `DelayQueue.take()` throws `InterruptedException` on interrupt; the worker loop catches it, restores the interrupt flag, and exits. This is the idiomatic pattern for a thread that owns a blocking queue.
- **Cancellation check after take**: the worker must check `task.isCancelled()` after `take()` returns and skip the action if cancelled. Cancelled tasks evaporate — they are never re-queued.

## Run
```
mvn -pl practice test -Dtest=TaskSchedulerTest
```

## Reference
- Worked solution: `solution/src/main/java/org/kata/scheduler/`
- Java Interview Primer: Q174 (scheduleAtFixedRate vs withFixedDelay), Q255 (DelayQueue/producer-consumer)
