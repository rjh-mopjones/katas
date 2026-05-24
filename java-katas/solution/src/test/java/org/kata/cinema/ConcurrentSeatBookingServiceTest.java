package org.kata.cinema;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentSeatBookingServiceTest {

    private final SeatBookingService service = new ConcurrentSeatBookingService();
    private final UUID screening = UUID.randomUUID();

    @Test
    void hold_then_confirm_creates_booking() {
        var seats = Set.of(new Seat(1, 1), new Seat(1, 2));
        var now = Instant.now();
        var hold = service.hold(screening, seats, Duration.ofMinutes(5), now).orElseThrow();
        var booking = service.confirm(hold.id(), "alice", now).orElseThrow();
        assertEquals(seats, booking.seats());
    }

    @Test
    void second_hold_on_same_seat_fails() {
        var now = Instant.now();
        service.hold(screening, Set.of(new Seat(1, 1)), Duration.ofMinutes(5), now);
        var second = service.hold(screening, Set.of(new Seat(1, 1)), Duration.ofMinutes(5), now);
        assertFalse(second.isPresent());
    }

    @Test
    void expired_hold_releases_seat() {
        var now = Instant.now();
        service.hold(screening, Set.of(new Seat(1, 1)), Duration.ofMillis(1), now);
        var afterExpiry = now.plus(Duration.ofSeconds(1));
        var second = service.hold(screening, Set.of(new Seat(1, 1)), Duration.ofMinutes(5), afterExpiry);
        assertTrue(second.isPresent());
    }

    @Test
    void confirm_is_idempotent() {
        var now = Instant.now();
        var hold = service.hold(screening, Set.of(new Seat(1, 1)), Duration.ofMinutes(5), now).orElseThrow();
        var first = service.confirm(hold.id(), "alice", now).orElseThrow();
        var second = service.confirm(hold.id(), "alice", now).orElseThrow();
        assertEquals(first.id(), second.id());
    }

    @Test
    void concurrent_confirms_of_same_hold_produce_single_booking() throws Exception {
        // Idempotency under contention: N threads all call confirm(holdId) simultaneously.
        // Exactly one booking should result, but ALL threads should observe the same booking id.
        var now = Instant.now();
        var hold = service.hold(screening, Set.of(new Seat(5, 5)), Duration.ofMinutes(5), now).orElseThrow();

        int N = 50;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var seenIds = ConcurrentHashMap.newKeySet();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    service.confirm(hold.id(), "guest" + i, now)
                            .ifPresent(b -> seenIds.add(b.id()));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(1, seenIds.size(), "all confirms must observe the same booking id");
    }

    @Test
    void concurrent_holds_on_same_seat_only_one_wins() throws Exception {
        var seat = new Seat(10, 10);
        var now = Instant.now();

        int N = 100;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var winners = new AtomicInteger();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    service.hold(screening, Set.of(seat), Duration.ofMinutes(5), now)
                            .ifPresent(h -> winners.incrementAndGet());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(1, winners.get());
    }
}
