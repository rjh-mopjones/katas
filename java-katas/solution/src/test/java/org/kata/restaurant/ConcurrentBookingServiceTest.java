package org.kata.restaurant;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrentBookingServiceTest {

    /**
     * COLLISION TEST — proves the per-table lock ACTUALLY works.
     *
     * Setup: 1 table, 1 slot, 100 threads all trying to book the SAME thing.
     * Expected: exactly 1 thread gets Optional.of(booking), the other 99 get Optional.empty().
     *
     * Uses the CountDownLatch start-gate pattern (Goetz, Java Concurrency in Practice, listing 5.11).
     *
     * ┌─────────────────────────────────────────────────────────────────────┐
     * │ WHY THE LATCH?                                                      │
     * │ Without it, threads start one-at-a-time (~1ms gap between spawns).  │
     * │ Thread 0 finishes its book() before thread 50 even starts running.  │
     * │ Result: no actual race, test passes even with broken locking.       │
     * │                                                                     │
     * │ The latch forces ALL threads to park at a checkpoint, then releases │
     * │ them simultaneously. That's how you create real contention.         │
     * └─────────────────────────────────────────────────────────────────────┘
     */
    @Test
    void only_one_of_many_concurrent_bookings_for_same_slot_succeeds() throws Exception {
        // ─── ARRANGE ───────────────────────────────────────────────────────
        var service = new ConcurrentBookingService(List.of(new Table(1, 4)));
        var slot = new TimeSlot(LocalDateTime.parse("2026-05-10T19:00"), Duration.ofHours(2));
        int threads = 100;

        // startGate: count=1. All N workers park on await(). One countDown()
        // releases them simultaneously — that's how we force a real collision
        // (without it threads trickle in and never actually race).
        var startGate = new CountDownLatch(1);

        // ─── ACT ───────────────────────────────────────────────────────────

        // try-with-resources: ExecutorService is AutoCloseable since Java 19,
        // close() blocks until all tasks finish.
        // Virtual threads — cheap, no pool sizing.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Submit as Callable<Optional<Booking>> — Callable can throw checked
            // exceptions (unlike Runnable), so startGate.await() needs no catch.
            // Any failure surfaces via Future.get() as ExecutionException.
            List<Future<Optional<Booking>>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> executor.submit(() -> {
                        startGate.await();
                        return service.book(2, slot, "User" + i);
                    }))
                    .toList();

            // Fire the gate — all parked workers race from here.
            startGate.countDown();

            // Future.get() blocks until that task completes and re-throws any
            // worker exception. Replaces both the `done` latch (futures = result
            // handles) and AtomicInteger (no concurrent counting needed).
            long successes = 0;
            for (var f : futures) {
                if (f.get().isPresent()) successes++;
            }

            // ─── ASSERT ────────────────────────────────────────────────────
            // With correct per-table locking: exactly one thread passes isFree()
            // + commits atomically; the other 99 see isFree() == false → empty.
            //
            // successes > 1 → locks broken. successes == 0 → workers crashed.
            assertEquals(1, successes, "exactly one thread should win the booking");
        }
    }


    /**
     * PARALLELISM TEST — proves the locks aren't TOO COARSE.
     *
     * Setup: 20 tables, 20 threads, each booking the SAME slot but free to pick ANY table.
     * Expected: all 20 threads succeed — different tables don't block each other.
     *
     * Uses the Future-collection pattern instead of CountDownLatch. Equally valid
     * for this test, slightly different shape — see header below.
     *
     * ┌─────────────────────────────────────────────────────────────────────┐
     * │ LATCH PATTERN vs FUTURE PATTERN — when to use each                  │
     * │                                                                     │
     * │ Latch pattern (the test above):                                     │
     * │   ✓ Forces simultaneous start — maximises contention                │
     * │   ✓ Necessary when you NEED a collision (the collision test)        │
     * │   ✓ Lets you use AtomicInteger for thread-safe counting             │
     * │   ✗ More boilerplate                                                │
     * │                                                                     │
     * │ Future pattern (this test):                                         │
     * │   ✓ Less ceremony — submit + collect results                        │
     * │   ✓ Future.get() blocks until that task completes                   │
     * │   ✓ Naturally serialises result collection (no atomic needed)       │
     * │   ✗ Tasks don't start at the same instant — weak contention         │
     * │                                                                     │
     * │ For PARALLELISM tests, weak contention is fine — you just want to   │
     * │ check that N tasks can complete without blocking each other.        │
     * │                                                                     │
     * │ For COLLISION tests, you NEED contention — use latches.             │
     * └─────────────────────────────────────────────────────────────────────┘
     */
    @Test
    void concurrent_bookings_on_different_tables_all_succeed() throws Exception {
        // ─── ARRANGE ───────────────────────────────────────────────────────
        int tableCount = 20;

        // 20 tables, ids 1..20, all capacity 4.
        var tables = IntStream.range(1, tableCount + 1)
                .mapToObj(id -> new Table(id, 4))
                .toList();

        var service = new ConcurrentBookingService(tables);

        // ONE slot — all 20 threads target the SAME slot. The only way for all
        // to succeed is for each thread to land on a different table.
        var slot = new TimeSlot(LocalDateTime.parse("2026-05-10T19:00"), Duration.ofHours(2));

        // ─── ACT ───────────────────────────────────────────────────────────

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Submit 20 tasks. Each returns a Future<Optional<Booking>> —
            // a handle representing "the eventual result of the booking attempt".
            List<Future<Optional<Booking>>> futures = IntStream.range(0, tableCount)
                    .mapToObj(i -> executor.submit(() -> service.book(2, slot, "User" + i)))
                    .toList();

            // Collect which TABLE each booking landed on. We need uniqueness,
            // not just success count — see assertions below for why.
            var bookedTableIds = new ArrayList<Integer>();
            for (Future<Optional<Booking>> f : futures) {
                f.get().ifPresent(b -> bookedTableIds.add(b.table().id()));
            }

            // ─── ASSERT ────────────────────────────────────────────────────
            //
            // Two invariants, each catching a different failure mode:
            //
            // (1) All 20 attempts succeed — catches deadlocks, exceptions,
            //     or locks so coarse that some attempts time out / get
            //     starved. (Weak on its own: a broken lock allowing
            //     double-booking would also satisfy this.)
            assertEquals(tableCount, bookedTableIds.size(),
                    "all 20 booking attempts should succeed");

            // (2) All bookings on DISTINCT tables — catches the real bug
            //     this test exists to detect. If the per-table lock is
            //     broken, two threads can both pass isFree(table_k) and
            //     double-book it. Both return Optional.of(...) so (1)
            //     still passes — only the Set size drops below 20.
            assertEquals(tableCount, Set.copyOf(bookedTableIds).size(),
                    "each table should be booked by exactly one thread");
        }
    }
}
