# Task Scheduler

> Build a one-shot delay scheduler using a DelayQueue — the primitive that drives ScheduledThreadPoolExecutor.

## The problem
Implement a `TaskScheduler` that accepts one-shot `Runnable` tasks with a delay and executes them on a single background worker thread when their time is due. The scheduler must order tasks by due time, sleep efficiently (no busy-waiting or polling loops), support pre-run cancellation, and shut down cleanly when closed.

## Requirements
- Starting the scheduler launches the worker thread; starting one that's already running throws `IllegalStateException`.
- Scheduling a task with a delay enqueues it and returns a handle representing that scheduled task. Scheduling throws if the scheduler is not running, the action is null, the delay is negative, or the time unit is null.
- Cancelling a scheduled task (via its handle) marks it cancelled; the worker skips it if cancellation happens before execution. Cancelling is idempotent.
- Tasks execute in due-time order; a newly scheduled task with a sooner due time should preempt the worker's current wait.
- The worker thread must not busy-wait — it must block until the next task is due.
- Closing the scheduler (it is `AutoCloseable`) interrupts the worker, waits up to 1 second for it to exit, and prevents further scheduling.
- If a task's action throws, the exception is swallowed so the worker continues draining the queue.

## What you're given
Nothing but the problem — you design the whole API and implementation from scratch.

## Run

There are no tests here — **write your own** under `src/test/java/org/kata/scheduler/` to drive your
implementation, then:

```
mvn -pl practice test
```

The reference tests in the `solution/` twin show one way to pin the behaviour — compare after you
have your own attempt.
