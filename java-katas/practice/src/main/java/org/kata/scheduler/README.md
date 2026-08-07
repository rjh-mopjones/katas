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

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/scheduler/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
