package org.kata.scheduler;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A delay-based, one-shot task scheduler backed by a {@link DelayQueue}.
 *
 * <h2>Motivation</h2>
 * Sometimes you need to run a task after a delay without the weight of a full
 * {@link java.util.concurrent.ScheduledThreadPoolExecutor}. Understanding the primitive
 * ({@code DelayQueue}) is a common interview question.
 *
 * <h2>Why DelayQueue?</h2>
 * {@link DelayQueue} is a priority blocking queue whose elements implement {@link Delayed}.
 * {@code take()} blocks the caller until the head element's delay has expired — no busy-wait,
 * no polling, no {@code Thread.sleep} in a loop. The element naturally "becomes due" and
 * unblocks the worker. This is the right primitive because:
 * <ul>
 *   <li>The worker thread consumes zero CPU while waiting — it is parked by the OS.</li>
 *   <li>Elements are ordered by due-time (min-heap internally), so the worker always picks the
 *       soonest task without scanning.</li>
 *   <li>Late-added tasks with a sooner due-time preempt the current wait because the queue
 *       unparks the waiting thread to re-examine the new head.</li>
 * </ul>
 *
 * <h2>Production equivalent: {@link java.util.concurrent.ScheduledThreadPoolExecutor}</h2>
 * {@code ScheduledThreadPoolExecutor} (STPE) is backed by a {@code DelayedWorkQueue} — a
 * custom heap with the same blocking semantics. STPE adds:
 * <ul>
 *   <li>A configurable thread pool (multiple workers for concurrent tasks).</li>
 *   <li>{@code scheduleAtFixedRate} and {@code scheduleWithFixedDelay} for recurring tasks.</li>
 *   <li>Shutdown semantics: run/cancel pending tasks on shutdown.</li>
 * </ul>
 * Know both for an interview: the primitive (DelayQueue) and the library class (STPE).
 *
 * <h2>Fixed-rate vs fixed-delay (primer Q174)</h2>
 * <ul>
 *   <li><b>Fixed-rate</b> — the next execution is scheduled relative to the <em>start</em> of the
 *       previous execution. If the task takes 200ms and the period is 1s, the next run starts
 *       800ms after the previous start. Tasks "catch up" if they fall behind. Useful for
 *       heartbeats and metrics sampling where absolute timing matters.</li>
 *   <li><b>Fixed-delay</b> — the next execution is scheduled relative to the <em>end</em> of the
 *       previous execution. Always at least {@code delay} time between runs. Safer for tasks that
 *       depend on the previous run completing (e.g., a DB cleanup that must not overlap).</li>
 * </ul>
 * This scheduler implements one-shot scheduling only. To build fixed-rate, reschedule the task
 * at the end of each execution: {@code schedule(() -> { action.run(); reschedule(...); }, period, unit)}.
 *
 * <h2>Lifecycle: start() / close()</h2>
 * {@link #start()} launches the worker thread. {@link #close()} (AutoCloseable) interrupts the
 * worker and waits for it to terminate. This pattern is idiomatic for resources that own a
 * background thread.
 *
 * <h2>Cancellation</h2>
 * {@link #schedule} returns a {@link ScheduledTask} handle. Calling {@link ScheduledTask#cancel()}
 * marks the task cancelled before it runs. The worker skips cancelled tasks without executing them.
 * Note: once a task has started executing, cancellation has no effect — this is a one-shot "before
 * execution" cancel, not an interrupt of a running task. For interruptible cancellation, the action
 * would need to check {@code Thread.currentThread().isInterrupted()} internally.
 */
public class TaskScheduler implements AutoCloseable {

    /**
     * A scheduled unit of work. Implements {@link Delayed} so it can live in a {@link DelayQueue}.
     *
     * <p>Implements {@link ScheduledTask} to give callers a cancel handle without exposing
     * the internal {@link Delayed} machinery.
     */
    static final class ScheduledTask implements Delayed {

        private final Runnable action;
        private final long dueNanos;      // System.nanoTime() value at which this task is due
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        ScheduledTask(Runnable action, long dueNanos) {
            this.action = action;
            this.dueNanos = dueNanos;
        }

        /**
         * Cancel this task before it executes. Safe to call from any thread; idempotent.
         * Has no effect if the task is already running or completed.
         */
        public void cancel() {
            cancelled.set(true);
        }

        /** Returns true if this task has been cancelled. */
        public boolean isCancelled() {
            return cancelled.get();
        }

        /**
         * Returns the remaining delay until this task is due.
         * {@link DelayQueue#take()} uses this to block until the head element returns <= 0.
         */
        @Override
        public long getDelay(TimeUnit unit) {
            // System.nanoTime() is the correct choice here: we stored dueNanos as a nanoTime
            // value, so we must compare against nanoTime. Using currentTimeMillis for one and
            // nanoTime for the other would corrupt the delay calculation.
            long remaining = dueNanos - System.nanoTime();
            return unit.convert(remaining, TimeUnit.NANOSECONDS);
        }

        /**
         * Total ordering by due time. DelayQueue uses this to maintain the min-heap: the
         * task due soonest is always at the head.
         */
        @Override
        public int compareTo(Delayed other) {
            if (other instanceof ScheduledTask that) {
                // Long.compare avoids integer overflow that could occur with naive subtraction
                // when the difference exceeds Integer.MAX_VALUE.
                return Long.compare(this.dueNanos, that.dueNanos);
            }
            // Fallback: compare via getDelay if the other Delayed is a different implementation.
            long diff = this.getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
            return Long.signum(diff);
        }
    }

    // The blocking priority queue. Items become "takeable" when their delay expires.
    private final DelayQueue<ScheduledTask> queue = new DelayQueue<>();

    // The single worker thread that drains the queue and executes tasks.
    private Thread worker;

    // Guards against double-start and post-close scheduling.
    private volatile boolean running = false;

    /**
     * Start the scheduler's worker thread. Must be called before {@link #schedule}.
     *
     * <p>A single worker thread is sufficient for this implementation because tasks are submitted
     * to the queue and picked up sequentially. If tasks are long-running and you need parallelism,
     * hand off each task to a thread pool inside the worker loop rather than executing it inline.
     *
     * @throws IllegalStateException if already started.
     */
    public synchronized void start() {
        if (running) throw new IllegalStateException("scheduler already started");
        running = true;

        // Use a daemon thread so the JVM can exit even if the scheduler is not explicitly closed.
        // In production code, prefer a named non-daemon thread and rely on close() for lifecycle.
        ThreadFactory factory = Thread.ofVirtual().factory();
        worker = factory.newThread(this::workerLoop);
        worker.setName("task-scheduler-worker");
        worker.start();
    }

    /**
     * Schedule a one-shot task to run after the specified delay.
     *
     * <p>The task is inserted into the {@link DelayQueue} immediately and will be executed by
     * the worker thread once {@code delay} time has elapsed. Multiple tasks can be queued;
     * they will execute in due-time order.
     *
     * @param action the work to perform when the delay expires; must not be null.
     * @param delay  how long to wait before executing; must be non-negative.
     * @param unit   the time unit of {@code delay}; must not be null.
     * @return a handle that can be used to cancel the task before it executes.
     * @throws IllegalStateException if the scheduler has not been started or has been closed.
     */
    public ScheduledTask schedule(Runnable action, long delay, TimeUnit unit) {
        if (!running) throw new IllegalStateException("scheduler is not running");
        if (action == null) throw new IllegalArgumentException("action must not be null");
        if (delay < 0)      throw new IllegalArgumentException("delay must be non-negative");
        if (unit == null)   throw new IllegalArgumentException("unit must not be null");

        // Compute the absolute due time in nanoTime units. Using nanoTime (not currentTimeMillis)
        // because nanoTime is monotonic — safe for measuring elapsed durations.
        long dueNanos = System.nanoTime() + unit.toNanos(delay);
        ScheduledTask task = new ScheduledTask(action, dueNanos);
        queue.put(task);   // put never blocks on an unbounded queue
        return task;
    }

    /**
     * Stop the scheduler and wait for the worker thread to terminate.
     *
     * <p>After this call, no further tasks will be executed — including tasks already in the queue
     * that have not yet become due. If you need to drain pending tasks on shutdown, you could
     * consume remaining elements from the queue here; this implementation discards them for
     * simplicity, which is the correct behaviour for test teardown.
     */
    @Override
    public synchronized void close() {
        if (!running) return;
        running = false;
        if (worker != null) {
            // Interrupting the worker unblocks DelayQueue.take() and causes the workerLoop
            // to check the running flag and exit gracefully.
            worker.interrupt();
            try {
                // Wait for the worker to acknowledge the interrupt and finish its current task.
                worker.join(1_000);  // 1-second timeout to avoid hanging the caller on close()
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }

    // ---- Worker loop ----

    /**
     * The main loop executed by the worker thread. Blocks on {@link DelayQueue#take()} until
     * the next task is due, then runs it (unless cancelled).
     *
     * <p>The loop exits when the thread is interrupted ({@code InterruptedException} from
     * {@code take}) or when {@code running} is false. Both are set by {@link #close()}.
     */
    private void workerLoop() {
        while (running) {
            try {
                // take() parks the thread until the head element's delay <= 0.
                // This is the key advantage over a sleep loop: the OS wakes us at exactly
                // the right time, without polling or busy-waiting.
                ScheduledTask task = queue.take();

                if (task.isCancelled()) {
                    // Task was cancelled between scheduling and execution — skip it.
                    // No need to return it to the queue; it simply evaporates.
                    continue;
                }

                // Execute the task. Any exception from the action is caught so the worker
                // thread survives and continues draining the queue. In production you'd log
                // the exception or route to a dead-letter mechanism.
                try {
                    task.action.run();
                } catch (Exception ex) {
                    // Intentional suppression: one bad task must not kill the scheduler.
                    // See class Javadoc for the error-isolation rationale.
                }

            } catch (InterruptedException e) {
                // close() interrupted us — restore the interrupt flag and exit the loop.
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
