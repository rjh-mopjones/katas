package org.kata.parking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentParkingLotTest {

    @Test
    void best_fit_picks_smallest_compatible_spot() {
        var compact = new CompactSpot(1);
        var standard = new StandardSpot(2);
        var truck = new TruckSpot(3);
        var lot = new ConcurrentParkingLot(List.of(truck, standard, compact));   // any input order
        var car = new Vehicle("ABC123", VehicleType.CAR);

        var ticket = lot.park(car, Instant.now()).orElseThrow();
        assertInstanceOf(CompactSpot.class, ticket.spot());   // smallest that fits
    }

    @Test
    void ev_cannot_park_in_compact_only_lot() {
        var lot = new ConcurrentParkingLot(List.of(new CompactSpot(1)));
        var ev = new Vehicle("EV999", VehicleType.EV);
        assertFalse(lot.park(ev, Instant.now()).isPresent());
    }

    @Test
    void unpark_charges_at_least_one_hour() {
        var lot = new ConcurrentParkingLot(List.of(new StandardSpot(1)));
        var car = new Vehicle("ABC123", VehicleType.CAR);
        Instant entry = Instant.now();
        var ticket = lot.park(car, entry).orElseThrow();
        var charge = lot.unpark(ticket.id(), entry.plus(Duration.ofMinutes(15))).orElseThrow();
        assertEquals(0, charge.compareTo(new java.math.BigDecimal("2.50")));   // 1-hour minimum
    }

    @Test
    void concurrent_parks_never_double_occupy() throws Exception {
        // 100 cars racing for 10 spots — exactly 10 should park, no spot occupied twice.
        var spots = IntStream.range(0, 10).<Spot>mapToObj(StandardSpot::new).toList();
        var lot = new ConcurrentParkingLot(spots);

        int N = 100;
        var gate = new CountDownLatch(1);
        var done = new CountDownLatch(N);
        var parked = new AtomicInteger();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, N).forEach(i -> exec.submit(() -> {
                try {
                    gate.await();
                    var car = new Vehicle("PLATE" + i, VehicleType.CAR);
                    lot.park(car, Instant.now()).ifPresent(t -> parked.incrementAndGet());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { done.countDown(); }
            }));
            gate.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
        }

        assertEquals(10, parked.get());
        assertEquals(0, lot.available(VehicleType.CAR));
    }
}
