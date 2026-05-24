package org.kata.restaurant;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates that InMemoryBookingService is NOT thread-safe.
 *
 * Two failure modes can manifest:
 *   1. ConcurrentModificationException — HashMap throws when iterated during mutation.
 *   2. Multiple successful bookings for the same slot/table (the check-then-act race).
 *
 * Either proves the service is unsafe under concurrency. ConcurrentBookingService
 * fixes both via per-table ReentrantLock + ConcurrentHashMap.
 */
class InMemoryBookingServiceRaceTest {

    @Test
    void unsynchronised_service_breaks_under_concurrency() throws Exception {
        var service = new InMemoryBookingService(List.of(new Table(1, 4)));
        var slot = new TimeSlot(LocalDateTime.parse("2026-05-10T19:00"), Duration.ofHours(2));
        int threads = 200;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Optional<Booking>>> futures = IntStream.range(0, threads)
                    .mapToObj(i -> executor.submit(() -> service.book(2, slot, "User" + i)))
                    .toList();

            int successes = 0;
            int raceFailures = 0;
            for (Future<Optional<Booking>> f : futures) {
                try {
                    if (f.get().isPresent()) successes++;
                } catch (ExecutionException e) {
                    raceFailures++;   // CME or similar — proof of unsafe access
                }
            }

            // Either > 1 success (double-book) OR any race exception proves the bug.
            assertTrue(successes > 1 || raceFailures > 0,
                    "Expected race to manifest. successes=" + successes +
                    ", raceFailures=" + raceFailures);
        }
    }
}
