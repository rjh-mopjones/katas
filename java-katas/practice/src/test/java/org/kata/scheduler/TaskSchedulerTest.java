package org.kata.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TaskSchedulerTest {

    // Shared scheduler instance; started before each test and closed after.
    // Tests use real wall-clock delays (small, tens of ms) because DelayQueue operates on
    // actual time — there is no injectable clock seam (the Delayed contract uses System.nanoTime).
    private TaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TaskScheduler();
        scheduler.start();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    void scheduled_task_runs_after_delay() throws Exception {
        // Schedule a task with a small delay and wait for it to execute.
        // The CountDownLatch provides a thread-safe "did it run?" signal with a bounded timeout.
        var latch = new CountDownLatch(1);

        scheduler.schedule(latch::countDown, 30, TimeUnit.MILLISECONDS);

        // Allow up to 2 seconds — the task should complete in ~30ms. A large timeout makes the
        // test robust against CI machine scheduling jitter while still catching genuine failures.
        assertTrue(latch.await(2, TimeUnit.SECONDS), "task must run within 2 seconds of scheduling");
    }

    @Test
    void tasks_run_in_due_time_order() throws Exception {
        // Schedule three tasks out of order (C first, A last). They should execute in due-time
        // order (A, B, C) regardless of submission order. DelayQueue's min-heap guarantees this.
        var executionOrder = new CopyOnWriteArrayList<String>();
        var allDone = new CountDownLatch(3);

        // Task C: due in 90ms
        scheduler.schedule(() -> { executionOrder.add("C"); allDone.countDown(); }, 90, TimeUnit.MILLISECONDS);
        // Task A: due in 10ms — submitted after C but due first
        scheduler.schedule(() -> { executionOrder.add("A"); allDone.countDown(); }, 10, TimeUnit.MILLISECONDS);
        // Task B: due in 50ms
        scheduler.schedule(() -> { executionOrder.add("B"); allDone.countDown(); }, 50, TimeUnit.MILLISECONDS);

        assertTrue(allDone.await(3, TimeUnit.SECONDS), "all tasks must complete");
        assertEquals(java.util.List.of("A", "B", "C"), executionOrder,
                "tasks must execute in due-time order, not submission order");
    }

    @Test
    void cancel_before_due_prevents_execution() throws Exception {
        var ran = new AtomicBoolean(false);

        // Schedule with a generous delay so we have time to cancel it.
        var task = scheduler.schedule(() -> ran.set(true), 200, TimeUnit.MILLISECONDS);
        task.cancel();

        // Wait longer than the original delay to confirm the task did not run.
        Thread.sleep(300);
        assertFalse(ran.get(), "cancelled task must not execute");
    }

    @Test
    void cancel_after_execution_is_a_no_op() throws Exception {
        var latch = new CountDownLatch(1);
        var task = scheduler.schedule(latch::countDown, 20, TimeUnit.MILLISECONDS);

        // Wait for the task to complete, then cancel — must be idempotent and not throw.
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertDoesNotThrow(task::cancel, "cancel after execution must not throw");
    }

    @Test
    void cancel_is_idempotent() {
        var task = scheduler.schedule(() -> {}, 500, TimeUnit.MILLISECONDS);
        // Multiple cancel calls must not throw.
        assertDoesNotThrow(task::cancel);
        assertDoesNotThrow(task::cancel);
        assertDoesNotThrow(task::cancel);
    }

    @Test
    void throwing_task_does_not_kill_the_scheduler() throws Exception {
        // A task that throws must not terminate the worker thread. A subsequent task must still run.
        var latch = new CountDownLatch(1);

        scheduler.schedule(() -> { throw new RuntimeException("bad task"); }, 10, TimeUnit.MILLISECONDS);
        scheduler.schedule(latch::countDown, 50, TimeUnit.MILLISECONDS);

        assertTrue(latch.await(2, TimeUnit.SECONDS),
                "worker must survive a throwing task and execute subsequent tasks");
    }

    @Test
    void zero_delay_task_runs_immediately() throws Exception {
        var latch = new CountDownLatch(1);
        scheduler.schedule(latch::countDown, 0, TimeUnit.MILLISECONDS);
        assertTrue(latch.await(2, TimeUnit.SECONDS), "zero-delay task must run without delay");
    }

    @Test
    void multiple_tasks_all_execute() throws Exception {
        int N = 5;
        var latch = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            int delayMs = (i + 1) * 10;   // 10, 20, 30, 40, 50ms
            scheduler.schedule(latch::countDown, delayMs, TimeUnit.MILLISECONDS);
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "all " + N + " tasks must complete");
    }

    @Test
    void schedule_throws_when_scheduler_not_started() {
        // A freshly constructed but not-yet-started scheduler must reject schedule calls.
        var unstarted = new TaskScheduler();
        assertThrows(IllegalStateException.class,
                () -> unstarted.schedule(() -> {}, 10, TimeUnit.MILLISECONDS));
    }

    @Test
    void start_twice_throws() {
        assertThrows(IllegalStateException.class, scheduler::start,
                "second start() must throw — scheduler is already running");
    }

    @Test
    void close_is_idempotent() {
        // close() on a running scheduler, then close() again — must not throw.
        scheduler.close();
        assertDoesNotThrow(scheduler::close);
    }
}
